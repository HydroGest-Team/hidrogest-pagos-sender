package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.gateway;

import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.client.ErpApiException;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.client.ErpFeignClient;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpComprobanteDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.mapper.ErpMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ErpGateway implements DestinationGateway {

    private final ErpFeignClient feignClient;
    private final ErpMapper mapper;

    public ErpGateway(ErpFeignClient feignClient, ErpMapper mapper) {
        this.feignClient = feignClient;
        this.mapper = mapper;
    }

    @Override
    @CircuitBreaker(name = "erp")
    @Retry(name = "erp")
    public DestinationGateway.SendResult send(Payment payment) {
        String correlationId = MDC.get("X-Correlation-Id");
        String idempotencyKey = payment.id() != null ? "EQA-" + String.format("%06d", payment.id()) : java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        try {
            var request = mapper.toRequest(payment, "EQUIPO_A", idempotencyKey);
            ErpComprobanteDto comprobante = feignClient.registrarPago(correlationId, null, null, request);
            return new DestinationGateway.SendResult(
                Outcome.DELIVERED,
                201,
                null,
                comprobante.getNumeroComprobante(),
                correlationId
            );
        } catch (ErpApiException e) {
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
    @CircuitBreaker(name = "erp")
    public Optional<DestinationGateway.Receipt> lookup(String idempotencyKey) {
        String correlationId = MDC.get("X-Correlation-Id");
        try {
            ErpComprobanteDto comprobante = feignClient.consultarPago(idempotencyKey, correlationId, null);
            return Optional.of(new DestinationGateway.Receipt(
                comprobante.getNumeroComprobante(),
                comprobante.getNumeroDocumentoOrigen(),
                comprobante.getEquipo() != null ? comprobante.getEquipo().getValue() : null,
                comprobante.getEstado() != null ? comprobante.getEstado().getValue() : null
            ));
        } catch (ErpApiException e) {
            if (e.getHttpStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @CircuitBreaker(name = "erp")
    public boolean health() {
        try {
            var health = feignClient.salud();
            return "ARRIBA".equals(health.getEstado());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Destino destino() {
        return Destino.ERP;
    }

    @Override
    public boolean supportsLookup() {
        return true;
    }

    private DestinationGateway.SendResult classifyError(ErpApiException e, String idempotencyKey, String correlationId) {
        int status = e.getHttpStatus();
        if (status == 201) {
            return new DestinationGateway.SendResult(Outcome.DELIVERED, 201, null, e.getComprobanteOriginal() != null ? e.getComprobanteOriginal().getNumeroComprobante() : null, correlationId);
        }
        if (status == 409) {
            if (e.isDocumentoDuplicado()) {
                return new DestinationGateway.SendResult(Outcome.DELIVERED, 409, "DOCUMENTO_DUPLICADO", e.getComprobanteOriginal() != null ? e.getComprobanteOriginal().getNumeroComprobante() : null, correlationId);
            }
            if (e.isDocumentoReutilizado()) {
                return new DestinationGateway.SendResult(Outcome.DEAD_LETTER, 409, "DOCUMENTO_REUTILIZADO", null, correlationId);
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