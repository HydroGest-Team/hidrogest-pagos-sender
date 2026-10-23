package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpComprobanteDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpRegistroPagoDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpSaludDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente del ERP escrito a mano (DTO generados de {@code erp-payments-api.yaml}). El ERP no usa
 * {@code Idempotency-Key}: la unicidad es {@code documento_origen.numero} dentro del body.
 */
@FeignClient(name = "erp", url = "${destinos.erp.base-url}", configuration = ErpFeignConfig.class)
public interface ErpFeignClient {

    @PostMapping(value = "/erp/v1/pagos", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ErpComprobanteDto registrarPagoErp(@RequestBody ErpRegistroPagoDto request);

    @GetMapping(value = "/erp/v1/pagos/{numeroDocumento}", produces = MediaType.APPLICATION_JSON_VALUE)
    ErpComprobanteDto consultarPagoErp(@PathVariable("numeroDocumento") String numeroDocumento);

    @GetMapping(value = "/erp/v1/salud", produces = MediaType.APPLICATION_JSON_VALUE)
    ErpSaludDto consultarSaludErp();
}
