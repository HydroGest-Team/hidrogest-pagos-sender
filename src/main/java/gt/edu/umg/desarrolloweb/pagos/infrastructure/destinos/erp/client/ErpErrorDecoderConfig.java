package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpErrorDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ErpErrorDecoderConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public ErrorDecoder erpErrorDecoder() {
        return (methodKey, response) -> {
            try {
                if (response.body() != null) {
                    String body = new String(response.body().asInputStream().readAllBytes());
                    ErpErrorDto error = objectMapper.readValue(body, ErpErrorDto.class);
                    return new ErpApiException(
                        response.status(),
                        error.getCodigoError(),
                        error.getMensaje(),
                        error.getReintentable() != null && error.getReintentable(),
                        error.getIdCorrelacion(),
                        error.getFechaHora() != null ? error.getFechaHora().toString() : null,
                        error.getErrores(),
                        error.getComprobanteOriginal()
                    );
                }
            } catch (IOException ignored) {
            }
            return new ErpApiException(
                response.status(),
                "UNKNOWN",
                "Error sin cuerpo estructurado: " + response.reason(),
                isRetryableStatus(response.status()),
                null,
                java.time.Instant.now().toString(),
                null,
                null
            );
        };
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }
}