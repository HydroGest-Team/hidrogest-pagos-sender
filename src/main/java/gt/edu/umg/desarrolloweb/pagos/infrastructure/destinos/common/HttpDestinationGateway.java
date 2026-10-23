package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import feign.RetryableException;
import gt.edu.umg.desarrolloweb.pagos.application.ResponseClassifier;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.LookupResult;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Base de los adaptadores HTTP: traduce respuestas y excepciones a {@link DeliveryResult} con la
 * tabla de decisión y pasa cada llamada por el circuit breaker del destino.
 * <ul>
 *   <li>Sin conexión (el request no salió): {@code RETRY}.</li>
 *   <li>Timeout de lectura u otra falla de red: {@code UNKNOWN} (pudo registrarse).</li>
 * </ul>
 * Para el breaker, un 4xx de negocio cuenta como éxito (el destino está sano); fallan RETRY y UNKNOWN.
 */
public abstract class HttpDestinationGateway implements DestinationGateway {

    private final Destino destino;
    private final CircuitBreaker breaker;

    protected HttpDestinationGateway(Destino destino, CircuitBreakerRegistry registry) {
        this.destino = destino;
        this.breaker = registry.circuitBreaker(destino.slug());
    }

    /** Envía el pago y devuelve el comprobante. Lanza {@link DestinationHttpException} si no es 2xx. */
    protected abstract String doSend(Payment payment, String idempotencyKey);

    /** Consulta por llave y devuelve el comprobante. Lanza {@link DestinationHttpException} si no es 2xx. */
    protected abstract String doLookup(String idempotencyKey);

    protected abstract boolean doHealth();

    @Override
    public Destino destino() {
        return destino;
    }

    @Override
    public boolean supportsLookup() {
        return true;
    }

    @Override
    public DeliveryResult send(Payment payment, String idempotencyKey) {
        if (!breaker.tryAcquirePermission()) {
            return DeliveryResult.circuitOpen();
        }
        long start = System.nanoTime();
        DeliveryResult result;
        try {
            result = DeliveryResult.delivered(201, null, doSend(payment, idempotencyKey));
        } catch (DestinationHttpException e) {
            Outcome outcome = ResponseClassifier.classify(e.status(), e.code());
            String receipt = outcome == Outcome.DELIVERED ? e.originalReceiptId() : null;
            result = new DeliveryResult(outcome, e.status(), e.code(), receipt, e.retryAfter(), null);
        } catch (IllegalArgumentException | ArithmeticException e) {
            // El pago no se pudo mapear al contrato (método no soportado, monto con más decimales...).
            breaker.releasePermission();
            return DeliveryResult.validacionLocal(e.getMessage());
        } catch (RuntimeException e) {
            result = fromTransportError(e);
        }
        record(result, start);
        return result;
    }

    @Override
    public LookupResult lookup(String idempotencyKey) {
        if (!breaker.tryAcquirePermission()) {
            return new LookupResult.Failed(DeliveryResult.circuitOpen());
        }
        long start = System.nanoTime();
        LookupResult lookup;
        DeliveryResult forBreaker;
        try {
            lookup = new LookupResult.Found(doLookup(idempotencyKey));
            forBreaker = DeliveryResult.delivered(200, null, null);
        } catch (DestinationHttpException e) {
            if (e.status() == 404) {
                lookup = new LookupResult.NotFound();
                forBreaker = DeliveryResult.delivered(404, e.code(), null);
            } else {
                Outcome outcome = ResponseClassifier.classify(e.status(), e.code());
                // Un 4xx en la consulta (403 por llave) es un problema de configuración: dead-letter.
                forBreaker = new DeliveryResult(outcome == Outcome.DELIVERED ? Outcome.UNKNOWN : outcome,
                        e.status(), e.code(), null, e.retryAfter(), "falló la consulta de reconciliación");
                lookup = new LookupResult.Failed(forBreaker);
            }
        } catch (RuntimeException e) {
            forBreaker = fromTransportError(e);
            lookup = new LookupResult.Failed(forBreaker);
        }
        record(forBreaker, start);
        return lookup;
    }

    @Override
    public boolean healthy() {
        try {
            return doHealth();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public CircuitBreaker.State circuitState() {
        return breaker.getState();
    }

    private void record(DeliveryResult result, long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        if (result.outcome() == Outcome.RETRY || result.outcome() == Outcome.UNKNOWN) {
            breaker.onError(elapsed, TimeUnit.NANOSECONDS, new IllegalStateException(String.valueOf(result.errorCode())));
        } else {
            breaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
        }
    }

    static DeliveryResult fromTransportError(RuntimeException e) {
        Throwable cause = e instanceof RetryableException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof ConnectException || cause instanceof UnknownHostException
                || cause instanceof NoRouteToHostException) {
            return new DeliveryResult(Outcome.RETRY, null, "SIN_CONEXION", null, null, cause.getClass().getSimpleName());
        }
        if (cause instanceof SocketTimeoutException) {
            String detail = cause.getMessage() != null && cause.getMessage().toLowerCase().contains("connect")
                    ? "timeout de conexión" : "timeout de lectura";
            Outcome outcome = detail.startsWith("timeout de conexión") ? Outcome.RETRY : Outcome.UNKNOWN;
            return new DeliveryResult(outcome, null, "TIMEOUT", null, null, detail);
        }
        // Cualquier otra falla (red cortada a mitad, cuerpo 2xx ilegible): no se sabe si quedó registrado.
        return new DeliveryResult(Outcome.UNKNOWN, null, "ERROR_RED", null, null, cause.getClass().getSimpleName());
    }
}
