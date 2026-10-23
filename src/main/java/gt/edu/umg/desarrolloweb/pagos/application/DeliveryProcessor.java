package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.AttemptLog;
import gt.edu.umg.desarrolloweb.pagos.domain.AttemptRecord;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.EstadoEntrega;
import gt.edu.umg.desarrolloweb.pagos.domain.LookupResult;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Consumer: procesa un mensaje (pago, destino) y decide qué hacer con él.
 * <ol>
 *   <li>Si ya está terminal, o el mensaje llegó antes de su hora (duplicado), se descarta.</li>
 *   <li>Reclama el envío con una escritura condicional; si otro consumer lo tiene, se descarta.</li>
 *   <li>Lee el pago de HidroGest y lo valida localmente.</li>
 *   <li>Si el intento anterior quedó incierto (timeout/504/caída), primero consulta al destino.</li>
 *   <li>Envía, registra el intento en la bitácora y cierra o reprograma el estado.</li>
 * </ol>
 * No hay reintentos ocultos: cada llamada es un intento y la espera la gobierna la cola.
 */
@Service
public class DeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);
    /** Margen para no descartar un mensaje de reintento que llega unos milisegundos antes. */
    private static final Duration TOLERANCIA_ADELANTO = Duration.ofSeconds(2);
    /** Espera mínima cuando el circuito del destino está abierto. */
    private static final Duration ESPERA_CIRCUITO = Duration.ofSeconds(30);

    private final DeliveryStateStore store;
    private final PaymentSource payments;
    private final AttemptLog attemptLog;
    private final DestinationRegistry destinations;
    private final PaymentValidator validator;
    private final RetryPolicy retryPolicy;
    private final DeliveryMetrics metrics;
    private final PagosProperties properties;
    private final Clock clock;

    public DeliveryProcessor(DeliveryStateStore store, PaymentSource payments, AttemptLog attemptLog,
                             DestinationRegistry destinations, PaymentValidator validator,
                             RetryPolicy retryPolicy, DeliveryMetrics metrics,
                             PagosProperties properties, Clock clock) {
        this.store = store;
        this.payments = payments;
        this.attemptLog = attemptLog;
        this.destinations = destinations;
        this.validator = validator;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    public DeliveryDecision process(DeliveryMessage message) {
        Instant now = clock.instant();
        Optional<DestinationGateway> gatewayOpt = destinations.gateway(message.destino());
        if (gatewayOpt.isEmpty() || !destinations.activos().contains(message.destino())) {
            log.warn("Mensaje para destino inactivo {}; se descarta", message.destino());
            return DeliveryDecision.ACK;
        }
        DestinationGateway gateway = gatewayOpt.get();

        Optional<DeliveryState> actual = store.find(message.paymentId(), message.destino());
        if (actual.isEmpty()) {
            log.warn("Mensaje sin estado en DynamoDB; se descarta");
            return DeliveryDecision.ACK;
        }
        DeliveryState estado = actual.get();
        if (estado.esTerminal()) {
            log.debug("Entrega ya {}; mensaje duplicado descartado", estado.estado());
            return DeliveryDecision.ACK;
        }
        if (estado.estado() != EstadoEntrega.EN_VUELO && estado.nextAttemptAt() != null
                && estado.nextAttemptAt().isAfter(now.plus(TOLERANCIA_ADELANTO))) {
            log.debug("Mensaje adelantado (próximo intento {}); el programado llegará a su hora", estado.nextAttemptAt());
            return DeliveryDecision.ACK;
        }

        Optional<DeliveryState> reclamo = store.claim(message.paymentId(), message.destino(), now,
                now.plus(properties.reconciliador().lease()));
        if (reclamo.isEmpty()) {
            log.debug("Otro consumer tiene el envío; mensaje descartado");
            return DeliveryDecision.ACK;
        }
        DeliveryState previo = reclamo.get();
        long version = previo.version() + 1;

        Optional<Payment> pago = payments.findById(message.paymentId());
        if (pago.isEmpty()) {
            return cerrarComoDeadLetter(message, version, previo.intentos(),
                    DeliveryResult.validacionLocal("el pago no existe en HidroGest"));
        }
        Optional<String> invalido = validator.validate(pago.get(), message.destino(), previo.idempotencyKey());
        if (invalido.isPresent()) {
            return cerrarComoDeadLetter(message, version, previo.intentos(),
                    DeliveryResult.validacionLocal(invalido.get()));
        }

        boolean incierto = previo.estado() == EstadoEntrega.DESCONOCIDO || previo.estado() == EstadoEntrega.EN_VUELO;
        if (incierto && gateway.supportsLookup()) {
            LookupResult consulta = timed(message, previo.intentos(), "LOOKUP",
                    () -> gateway.lookup(previo.idempotencyKey()), DeliveryProcessorSupport::asResult);
            if (consulta instanceof LookupResult.Found found) {
                log.info("El destino ya tenía el pago (comprobante {}); se marca ENTREGADO sin reenviar", found.receiptId());
                return cerrarComoEntregado(message, version, previo.intentos(),
                        DeliveryResult.delivered(200, "ENCONTRADO_EN_CONSULTA", found.receiptId()));
            }
            if (consulta instanceof LookupResult.Failed failed) {
                return failed.result().outcome() == Outcome.DEAD_LETTER
                        ? cerrarComoDeadLetter(message, version, previo.intentos() + 1, failed.result())
                        : reprogramar(message, previo, version, failed.result());
            }
            log.info("El destino no tiene el pago; se envía");
        }

        DeliveryResult resultado = timed(message, previo.intentos() + 1, "SEND",
                () -> gateway.send(pago.get(), previo.idempotencyKey()), r -> r);
        return switch (resultado.outcome()) {
            case DELIVERED -> cerrarComoEntregado(message, version, previo.intentos() + 1, resultado);
            case DEAD_LETTER -> cerrarComoDeadLetter(message, version, previo.intentos() + 1, resultado);
            case RETRY, UNKNOWN -> reprogramar(message, previo, version, resultado);
        };
    }

    private DeliveryDecision cerrarComoEntregado(DeliveryMessage message, long version, int intentos,
                                                 DeliveryResult resultado) {
        if (!store.markDelivered(message.paymentId(), message.destino(), version, intentos, resultado, clock.instant())) {
            log.warn("No se pudo cerrar como ENTREGADO: el estado cambió (versión {})", version);
            return DeliveryDecision.ACK;
        }
        log.info("ENTREGADO http={} code={} comprobante={}", resultado.httpStatus(), resultado.errorCode(), resultado.receiptId());
        metrics.entregaCerrada(message.destino(), EstadoEntrega.ENTREGADO.name());
        if (estaCompleto(message.paymentId())) {
            log.info("Pago completo: entregado a todos los destinos activos {}", destinations.activos());
            metrics.pagoCompleto();
        }
        return DeliveryDecision.ACK;
    }

    private DeliveryDecision cerrarComoDeadLetter(DeliveryMessage message, long version,
                                                  int intentos, DeliveryResult resultado) {
        if (!store.markDeadLetter(message.paymentId(), message.destino(), version, intentos, resultado, clock.instant())) {
            log.warn("No se pudo cerrar como DEAD_LETTER: el estado cambió (versión {})", version);
            return DeliveryDecision.ACK;
        }
        String causa = resultado.errorCode() + (resultado.detail() == null ? "" : ": " + resultado.detail());
        log.warn("DEAD_LETTER http={} causa={} intentos={}", resultado.httpStatus(), causa, intentos);
        metrics.entregaCerrada(message.destino(), EstadoEntrega.DEAD_LETTER.name());
        return new DeliveryDecision.DeadLetter(message, causa);
    }

    private DeliveryDecision reprogramar(DeliveryMessage message, DeliveryState previo, long version, DeliveryResult resultado) {
        boolean incierto = resultado.outcome() == Outcome.UNKNOWN
                || (resultado.esCircuitoAbierto()
                    && (previo.estado() == EstadoEntrega.DESCONOCIDO || previo.estado() == EstadoEntrega.EN_VUELO));
        EstadoEntrega siguiente = incierto ? EstadoEntrega.DESCONOCIDO : EstadoEntrega.REINTENTO;
        Duration espera;
        int intentos;
        if (resultado.esCircuitoAbierto()) {
            // El destino está caído: no se quema un intento de este pago.
            intentos = previo.intentos();
            espera = retryPolicy.atLeast(ESPERA_CIRCUITO);
        } else {
            intentos = previo.intentos() + 1;
            if (retryPolicy.agotado(intentos)) {
                DeliveryResult agotado = new DeliveryResult(Outcome.DEAD_LETTER, resultado.httpStatus(),
                        resultado.errorCode(), null, null, "intentos agotados (" + intentos + ")");
                return cerrarComoDeadLetter(message, version, intentos, agotado);
            }
            espera = retryPolicy.delayFor(intentos, resultado.retryAfter());
        }
        Instant proximo = clock.instant().plus(espera);
        if (!store.markRetry(message.paymentId(), message.destino(), version, siguiente, intentos, proximo, resultado, clock.instant())) {
            log.warn("No se pudo reprogramar: el estado cambió (versión {})", version);
            return DeliveryDecision.ACK;
        }
        log.info("{} http={} code={} intentos={} próximo intento en {}", siguiente,
                resultado.httpStatus(), resultado.errorCode(), intentos, espera);
        return new DeliveryDecision.Retry(message.nextAttempt(), espera);
    }

    private boolean estaCompleto(long paymentId) {
        List<DeliveryState> estados = store.findByPayment(paymentId);
        return destinations.activos().stream().allMatch(d -> estados.stream()
                .anyMatch(s -> s.destino() == d && s.estado() == EstadoEntrega.ENTREGADO));
    }

    /** Ejecuta la llamada, mide su duración y la registra en la bitácora y en las métricas. */
    private <T> T timed(DeliveryMessage message, int intento, String operacion, Supplier<T> call,
                        java.util.function.Function<T, DeliveryResult> asResult) {
        long inicio = System.nanoTime();
        T respuesta = call.get();
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);
        DeliveryResult r = asResult.apply(respuesta);
        if (!r.esCircuitoAbierto()) {
            metrics.intento(message.destino(), operacion, r.outcome(), duracion);
            attemptLog.record(new AttemptRecord(message.paymentId(), message.destino(), intento, clock.instant(),
                    operacion, r.outcome(), r.httpStatus(), r.errorCode(), r.receiptId(),
                    message.correlationId(), duracion.toMillis()));
        }
        return respuesta;
    }
}
