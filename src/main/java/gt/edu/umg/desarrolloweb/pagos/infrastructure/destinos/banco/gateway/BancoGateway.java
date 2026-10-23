package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.gateway;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.client.BancoApiException;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.client.BancoFeignClient;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.mapper.BancoMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BancoGateway implements DestinationGateway {

    private final BancoFeignClient feignClient;
    private final BancoMapper mapper;

    public BancoGateway(BancoFeignClient feignClient, BancoMapper mapper) {
        this.feignClient = feignClient;
        this.mapper = mapper;
    }

    @Override
    @CircuitBreaker(name = "banco")
    @Retry(name = "banco")
    public DestinationGateway.SendResult send(Payment payment) {
        String correlationId = MDC.get("X-Correlation-Id");
        String idempotencyKey = payment.id() != null ? "EQA-" + String.format("%06d", payment.id()) : java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        try {
            var request = mapper.toRequest(payment, "EQUIPO_A", idempotencyKey);
            BankPaymentReceiptDto receipt = feignClient.sendPayment(
                idempotencyKey,
                correlationId,
                null,
                null,
                request
            );
            return new DestinationGateway.SendResult(
                Outcome.DELIVERED,
                201,
                null,
                receipt.getReceiptId(),
                correlationId
            );
        } catch (BancoApiException e) {
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
    @CircuitBreaker(name = "banco")
    public Optional<DestinationGateway.Receipt> lookup(String idempotencyKey) {
        String correlationId = MDC.get("X-Correlation-Id");
        try {
            BankPaymentReceiptDto receipt = feignClient.getPayment(idempotencyKey, correlationId, null);
            return Optional.of(new DestinationGateway.Receipt(
                receipt.getReceiptId(),
                receipt.getIdempotencyKey(),
                receipt.getTeamCode() != null ? receipt.getTeamCode().getValue() : null,
                receipt.getStatus() != null ? receipt.getStatus().getValue() : null
            ));
        } catch (BancoApiException e) {
            if (e.getHttpStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @CircuitBreaker(name = "banco")
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
        return Destino.BANCO;
    }

    @Override
    public boolean supportsLookup() {
        return true;
    }

    private DestinationGateway.SendResult classifyError(BancoApiException e, String idempotencyKey, String correlationId) {
        int status = e.getHttpStatus();
        if (status == 201) {
            return new DestinationGateway.SendResult(Outcome.DELIVERED, 201, null, e.getOriginalReceipt() != null ? e.getOriginalReceipt().getReceiptId() : null, correlationId);
        }
        if (status == 409) {
            if (e.isDuplicatePayment()) {
                return new DestinationGateway.SendResult(Outcome.DELIVERED, 409, "DUPLICATE_PAYMENT", e.getOriginalReceipt() != null ? e.getOriginalReceipt().getReceiptId() : null, correlationId);
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