package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.*;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.config.MetricsService;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.config.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb.DynamoDbAttemptLogAdapter;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReconciliationService {

    private final PaymentSource paymentSource;
    private final DeliveryStateStore stateStore;
    private final DeliveryPublisher publisher;
    private final AttemptLogPort attemptLog;
    private final Map<Destino, DestinationGateway> gateways;
    private final PagosProperties properties;
    private final MetricsService metricsService;

    public ReconciliationService(PaymentSource paymentSource,
                                  DeliveryStateStore stateStore,
                                  DeliveryPublisher publisher,
                                  AttemptLogPort attemptLog,
                                  List<DestinationGateway> gateways,
                                  PagosProperties properties,
                                  MetricsService metricsService) {
        this.paymentSource = paymentSource;
        this.stateStore = stateStore;
        this.publisher = publisher;
        this.attemptLog = attemptLog;
        this.gateways = Map.of(
            Destino.BANCO, gateways.stream().filter(g -> g.destino() == Destino.BANCO).findFirst().orElseThrow(),
            Destino.ERP, gateways.stream().filter(g -> g.destino() == Destino.ERP).findFirst().orElseThrow(),
            Destino.TERCERO, gateways.stream().filter(g -> g.destino() == Destino.TERCERO).findFirst().orElseThrow()
        );
        this.properties = properties;
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${pagos.reconciliador.intervalo:120000}")
    public void reconcile() {
        Instant now = Instant.now();
        
        // 1. Reencolar estados abiertos con nextAttemptAt vencido
        List<DeliveryState> paraReintentar = stateStore.findAbiertosConNextAttemptAntesDe(now);
        for (DeliveryState state : paraReintentar) {
            reenqueue(state);
        }

        // 2. Reencolar EN_VUELO con lease vencido
        List<DeliveryState> enVueloVencidos = stateStore.findEnVueloConLeaseVencido(now);
        for (DeliveryState state : enVueloVencidos) {
            reenqueue(state);
        }

        // 3. Detectar PENDIENTE sin actividad (caída entre put y publish)
        Instant pendienteDesde = now.minus(properties.getReconciliador().getPendienteSinActividad());
        List<DeliveryState> pendientesInactivos = stateStore.findPendientesSinActividadDesde(pendienteDesde);
        for (DeliveryState state : pendientesInactivos) {
            reenqueue(state);
        }

        // 4. Barrido de consistencia: pagos PAGADO sin estado para destinos activos
        for (String destinoStr : properties.getActivos()) {
            Destino destino = Destino.valueOf(destinoStr);
            Instant desde = properties.getTerceroDesde() != null && !properties.getTerceroDesde().isBlank()
                ? java.time.Instant.parse(properties.getTerceroDesde())
                : Instant.EPOCH;
            
            List<Long> pagosSinEstado = stateStore.findPagosSinEstadoParaDestino(destino.name(), desde);
            for (Long paymentId : pagosSinEstado) {
                Payment payment = paymentSource.findById(paymentId);
                if (payment != null && payment.esElegible()) {
                    String idempotencyKey = properties.prefijo() + String.format("%06d", payment.id());
                    
                    DeliveryState newState = new DeliveryState();
                    newState.setPaymentId(payment.id());
                    newState.setDestino(destino.name());
                    newState.setEstado(EstadoEntrega.PENDIENTE.name());
                    newState.setIntentos(0);
                    newState.setIdempotencyKey(idempotencyKey);
                    newState.setNextAttemptAt(Instant.now());
                    
                    if (stateStore.createIfNotExists(newState)) {
                        MDC.put("X-Correlation-Id", UUID.randomUUID().toString());
                        publisher.publish(new DeliveryMessage(payment.id(), destino.name(), 0, MDC.get("X-Correlation-Id"), idempotencyKey));
                    }
                }
            }
        }

        // Metrics: pending counts per destination
        for (String destinoStr : properties.getActivos()) {
            long count = stateStore.findPendientesSinActividadDesde(Instant.EPOCH).stream()
                .filter(s -> destinoStr.equals(s.getDestino()))
                .count();
            metricsService.recordPendingCount(destinoStr, count);
        }
    }

    private void reenqueue(DeliveryState state) {
        MDC.put("X-Correlation-Id", UUID.randomUUID().toString());
        publisher.publish(new DeliveryMessage(
            state.getPaymentId(),
            state.getDestino(),
            state.getIntentos() + 1,
            MDC.get("X-Correlation-Id"),
            state.getIdempotencyKey()
        ));
    }

    public void reenqueueDeadLetter(Long paymentId, String destino) {
        Optional<DeliveryState> stateOpt = stateStore.findByPaymentIdAndDestino(paymentId, destino);
        stateOpt.ifPresent(s -> {
            if ("DEAD_LETTER".equals(s.getEstado())) {
                // Resetear a PENDIENTE para reintento manual
                DeliveryState newState = new DeliveryState();
                newState.setPaymentId(s.getPaymentId());
                newState.setDestino(s.getDestino());
                newState.setEstado(EstadoEntrega.PENDIENTE.name());
                newState.setIntentos(0);
                newState.setIdempotencyKey(s.getIdempotencyKey());
                newState.setNextAttemptAt(Instant.now());
                newState.setLastHttpStatus(null);
                newState.setLastErrorCode(null);
                stateStore.createIfNotExists(newState);
                
                MDC.put("X-Correlation-Id", UUID.randomUUID().toString());
                publisher.publish(new DeliveryMessage(s.getPaymentId(), s.getDestino(), 0, MDC.get("X-Correlation-Id"), s.getIdempotencyKey()));
            }
        });
    }

    public long countPendientes() {
        // Simplified: would need proper query in production
        return 0;
    }

    public boolean isPagoCompleto(Long paymentId) {
        return properties.getActivos().stream()
            .allMatch(destino -> {
                Optional<DeliveryState> state = stateStore.findByPaymentIdAndDestino(paymentId, destino);
                return state.isPresent() && state.get().isTerminal() && "ENTREGADO".equals(state.get().getEstado());
            });
    }
}