package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.gateway;

import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.client.TerceroApiException;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.client.TerceroFeignClient;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.mapper.TerceroMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TerceroGateway implements DestinationGateway {

    private final TerceroFeignClient feignClient;
    private final TerceroMapper mapper;

    public TerceroGateway(TerceroFeignClient feignClient, TerceroMapper mapper) {
        this.feignClient = feignClient;
        this.mapper = mapper;
    }

    @Override
    @CircuitBreaker(name = "tercero")
    @Retry(name = "tercero")
    public DestinationGateway.SendResult send(Payment payment) {
        String correlationId = MDC.get("X-Correlation-Id");
        String idempotencyKey = payment.id() != null ? "EQA-" + String.format("%06d", payment.id()) : java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        try {
            var request = mapper.toRequest(payment, "EQUIPO_A", idempotencyKey);
            ThirdReceiptDto receipt = feignClient.sendPayment(idempotencyKey, correlationId, null, null, request);
            return new DestinationGateway.SendResult(
                Outcome.DELIVERED,
                201,
                null,
                receipt.getConfirmationId(),
                correlationId
            );
        } catch (TerceroApiException e) {
            return classifyError(e, idempotencyKey, correlationId);
        } catch (Exception e) {
            return new DestinationGateway.SendResult(
                Outcome.RETRY,
                0,
                "CONNECTION_ERROR",
                null,
                correlationId
            );
        }
    }

    @Override
    @CircuitBreaker(name = "tercero")
    public Optional<DestinationGateway.Receipt> lookup(String idempotencyKey) {
        String correlationId = MDC.get("X-Correlation-Id");
        try {
            ThirdReceiptDto receipt = feignClient.getPayment(idempotencyKey, correlationId, null);
            return Optional.of(new DestinationGateway.Receipt(
                receipt.getConfirmationId(),
                receipt.getIdempotencyKey(),
                receipt.getTeamCode() != null ? receipt.getTeamCode().getValue() : null,
                receipt.getStatus() != null ? receipt.getStatus().getValue() : null
            ));
        } catch (TerceroApiException e) {
            if (e.getHttpStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @CircuitBreaker(name = "tercero")
    public boolean health() {
        try {
            var health = feignClient.health();
            return "UP".equals(health.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Destino destino() {
        return Destino.TERCERO;
    }

    @Override
    public boolean supportsLookup() {
        return true;
    }

    private DestinationGateway.SendResult classifyError(TerceroApiException e, String idempotencyKey, String correlationId) {
        int status = e.getHttpStatus();
        if (status == 201) {
            return new DestinationGateway.SendResult(Outcome.DELIVERED, 201, null, e.getOriginalConfirmation() != null ? e.getOriginalConfirmation().getConfirmationId() : null, correlationId);
        }
        if (status == 409) {
            if (e.isDuplicatePayment()) {
                return new DestinationGateway.SendResult(Outcome.DELIVERED, 409, "DUPLICATE_PAYMENT", e.getOriginalConfirmation() != null ? e.getOriginalConfirmation().getConfirmationId() : null, correlationId);
            }
            if (e.isIdempotencyKeyReused()) {
                return new DestinationGateway.SendResult(Outcome.DEAD_LETTER, 409, "IDEMPOTENCY_KEY_REUSED", null, correlationId);
            }
        }
        if (status == 400 || status == 403 || status == 422) {
            return new DestinationGateway.SendResult(Outcome.DEAD_LETTER, status, e.getErrorCode(), null, correlationId);
        }
        if (status == 429 || status == 500 || status == 502 || status == 503 || status == 504) {
            return new DestinationGateway.SendResult(Outcome.RETRY, status, e.getErrorCode(), null, correlationId);
        }
        return new DestinationGateway.SendResult(Outcome.UNKNOWN, status, e.getErrorCode(), null, correlationId);
    }
}