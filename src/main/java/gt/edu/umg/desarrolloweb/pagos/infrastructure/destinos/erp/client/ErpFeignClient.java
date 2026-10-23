package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.client;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpComprobanteDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpRegistroPagoDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpSaludDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "erp-client", url = "${erp.base-url}", configuration = ErpClientConfig.class)
public interface ErpFeignClient {

    @PostMapping(value = "/erp/v1/pagos", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ErpComprobanteDto registrarPago(
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader(value = "X-Simulation-Scenario", required = false) String simulationScenario,
        @RequestHeader("x-api-key") String apiKey,
        @RequestBody ErpRegistroPagoDto request
    );

    @GetMapping(value = "/erp/v1/pagos/{numeroDocumento}", produces = MediaType.APPLICATION_JSON_VALUE)
    ErpComprobanteDto consultarPago(
        @PathVariable("numeroDocumento") String numeroDocumento,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader("x-api-key") String apiKey
    );

    @GetMapping(value = "/erp/v1/salud", produces = MediaType.APPLICATION_JSON_VALUE)
    ErpSaludDto salud();
}