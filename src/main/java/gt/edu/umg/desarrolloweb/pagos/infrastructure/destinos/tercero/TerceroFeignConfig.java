package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.ContractErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.DestinosProperties;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.FeignDestinoSupport;
import org.springframework.context.annotation.Bean;

/** Configuración aislada del cliente de la tercera API (sin {@code @Configuration}, ver {@code BancoFeignConfig}). */
class TerceroFeignConfig {

    @Bean
    RequestInterceptor terceroHeaders(DestinosProperties destinos) {
        return FeignDestinoSupport.headers(destinos.tercero());
    }

    @Bean
    Request.Options terceroTimeouts(DestinosProperties destinos) {
        return FeignDestinoSupport.timeouts(destinos.tercero());
    }

    @Bean
    ErrorDecoder terceroErrorDecoder(ObjectMapper mapper) {
        return new ContractErrorDecoder(mapper, "code", "/originalConfirmation/confirmationId",
                "/originalConfirmation/amount", false);
    }

    @Bean
    Retryer terceroRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
