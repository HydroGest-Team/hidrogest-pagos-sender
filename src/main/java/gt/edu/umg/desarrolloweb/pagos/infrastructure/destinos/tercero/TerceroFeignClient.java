package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.HealthStatusDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdReceiptDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente de la tercera API (contrato <b>provisional</b> {@code third-payments-api.yaml}). Cuando llegue
 * el contrato real, solo cambia este paquete.
 */
@FeignClient(name = "tercero", url = "${destinos.tercero.base-url}", configuration = TerceroFeignConfig.class)
public interface TerceroFeignClient {

    @PostMapping(value = "/third/v1/payments", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ThirdReceiptDto sendThirdPayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @RequestBody ThirdPaymentRequestDto request);

    @GetMapping(value = "/third/v1/payments/{idempotencyKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    ThirdReceiptDto getThirdPayment(@PathVariable("idempotencyKey") String idempotencyKey);

    @GetMapping(value = "/third/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    HealthStatusDto getThirdHealth();
}
