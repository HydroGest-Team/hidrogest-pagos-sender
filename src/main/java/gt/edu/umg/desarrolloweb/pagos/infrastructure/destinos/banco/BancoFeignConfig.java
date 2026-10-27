package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.ContractErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.DestinosProperties;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.FeignDestinoSupport;
import org.springframework.context.annotation.Bean;

/**
 * Configuración del cliente Feign del banco. <b>Sin {@code @Configuration}</b> a propósito: así sus
 * beans viven solo en el contexto de este cliente y la llave del banco no llega a los otros destinos.
 */
class BancoFeignConfig {

    @Bean
    RequestInterceptor bancoHeaders(DestinosProperties destinos) {
        return FeignDestinoSupport.headers(destinos.banco());
    }

    @Bean
    Request.Options bancoTimeouts(DestinosProperties destinos) {
        return FeignDestinoSupport.timeouts(destinos.banco());
    }

    @Bean
    ErrorDecoder bancoErrorDecoder(ObjectMapper mapper) {
        return new ContractErrorDecoder(mapper, "code", "/originalReceipt/receiptId",
                "/originalReceipt/amount/valueInCents", true);
    }

    /** El reintento lo gobierna la cola, nunca Feign. */
    @Bean
    Retryer bancoRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
