package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.HealthStatusDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente del banco escrito a mano (los DTO sí se generan del contrato {@code bank-payments-api.yaml}).
 * {@code x-api-key}, {@code X-Correlation-Id} y {@code X-Simulation-Scenario} los agrega {@link BancoFeignConfig}.
 */
@FeignClient(name = "banco", url = "${destinos.banco.base-url}", configuration = BancoFeignConfig.class)
public interface BancoFeignClient {

    @PostMapping(value = "/bank/v1/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    BankPaymentReceiptDto sendBankPayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody BankPaymentRequestDto request);

    @GetMapping(value = "/bank/v1/payments/{idempotencyKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    BankPaymentReceiptDto getBankPayment(@PathVariable("idempotencyKey") String idempotencyKey);

    @GetMapping(value = "/bank/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    HealthStatusDto getBankHealth();
}
