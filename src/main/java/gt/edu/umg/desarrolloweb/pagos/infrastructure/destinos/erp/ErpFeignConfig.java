package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.ContractErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.DestinosProperties;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.FeignDestinoSupport;
import org.springframework.context.annotation.Bean;

/** Configuración aislada del cliente del ERP (sin {@code @Configuration}, ver {@code BancoFeignConfig}). */
class ErpFeignConfig {

    @Bean
    RequestInterceptor erpHeaders(DestinosProperties destinos) {
        return FeignDestinoSupport.headers(destinos.erp());
    }

    @Bean
    Request.Options erpTimeouts(DestinosProperties destinos) {
        return FeignDestinoSupport.timeouts(destinos.erp());
    }

    @Bean
    ErrorDecoder erpErrorDecoder(ObjectMapper mapper) {
        return new ContractErrorDecoder(mapper, "codigo_error", "/comprobante_original/numero_comprobante",
                "/comprobante_original/total", false);
    }

    @Bean
    Retryer erpRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
