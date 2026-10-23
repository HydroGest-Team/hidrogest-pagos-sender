package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.*;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.config.PagosProperties;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb.DynamoDbAttemptLogAdapter;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PaymentDispatcher {

    private final PaymentSource paymentSource;
    private final DeliveryStateStore stateStore;
    private final DeliveryPublisher publisher;
    private final ControlPort controlPort;
    private final AttemptLogPort attemptLog;
    private final PagosProperties properties;

    public PaymentDispatcher(PaymentSource paymentSource,
                              DeliveryStateStore stateStore,
                              DeliveryPublisher publisher,
                              ControlPort controlPort,
                              AttemptLogPort attemptLog,
                              PagosProperties properties) {
        this.paymentSource = paymentSource;
        this.stateStore = stateStore;
        this.publisher = publisher;
        this.controlPort = controlPort;
        this.attemptLog = attemptLog;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${pagos.producer.intervalo:10000}")
    @Transactional(readOnly = true)
    public void dispatch() {
        Instant cursor = controlPort.getCursor().orElse(Instant.EPOCH);
        Instant since = cursor.minus(properties.getProducer().getTraslape());
        
        List<Payment> pagos = paymentSource.findElegiblesSince(since, properties.getProducer().getLote());
        
        for (Payment pago : pagos) {
            for (String destinoStr : properties.getActivos()) {
                Destino destino = Destino.valueOf(destinoStr);
                String idempotencyKey = properties.prefijo() + String.format("%06d", pago.id());
                
                DeliveryState state = new DeliveryState();
                state.setPaymentId(pago.id());
                state.setDestino(destino.name());
                state.setEstado(EstadoEntrega.PENDIENTE.name());
                state.setIntentos(0);
                state.setIdempotencyKey(idempotencyKey);
                state.setNextAttemptAt(Instant.now());
                
                if (stateStore.createIfNotExists(state)) {
                    MDC.put("X-Correlation-Id", UUID.randomUUID().toString());
                    publisher.publish(new DeliveryMessage(pago.id(), destino.name(), 0, MDC.get("X-Correlation-Id"), idempotencyKey));
                }
            }
        }
        
        if (!pagos.isEmpty()) {
            Instant newCursor = pagos.stream()
                .map(Payment::updatedAt)
                .max(java.time.LocalDateTime::compareTo)
                .map(ldt -> ldt.atZone(Payment.ZONA_HORARIA).toInstant())
                .orElse(cursor);
            controlPort.updateCursor(newCursor);
        }
    }
}