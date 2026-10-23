package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.client;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.HealthStatusDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "tercero-client", url = "${tercero.base-url}", configuration = TerceroClientConfig.class)
public interface TerceroFeignClient {

    @PostMapping(value = "/third/v1/payments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ThirdReceiptDto sendPayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader(value = "X-Simulation-Scenario", required = false) String simulationScenario,
        @RequestHeader("x-api-key") String apiKey,
        @RequestBody ThirdPaymentRequestDto request
    );

    @GetMapping(value = "/third/v1/payments/{idempotencyKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    ThirdReceiptDto getPayment(
        @PathVariable("idempotencyKey") String idempotencyKey,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader("x-api-key") String apiKey
    );

    @GetMapping(value = "/third/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    HealthStatusDto health();
}