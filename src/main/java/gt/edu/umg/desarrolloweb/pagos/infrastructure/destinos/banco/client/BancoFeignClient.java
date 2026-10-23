package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.client;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "banco-client", url = "${banco.base-url}", configuration = BancoClientConfig.class)
public interface BancoFeignClient {

    @PostMapping(value = "/bank/v1/payments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    BankPaymentReceiptDto sendPayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader(value = "X-Simulation-Scenario", required = false) String simulationScenario,
        @RequestHeader("x-api-key") String apiKey,
        @RequestBody BankPaymentRequestDto request
    );

    @GetMapping(value = "/bank/v1/payments/{idempotencyKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    BankPaymentReceiptDto getPayment(
        @PathVariable("idempotencyKey") String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader("x-api-key") String apiKey
    );

    @GetMapping(value = "/bank/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.HealthStatusDto health();
}