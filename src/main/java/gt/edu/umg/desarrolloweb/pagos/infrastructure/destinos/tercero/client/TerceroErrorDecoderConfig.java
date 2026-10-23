package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdErrorDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class TerceroErrorDecoderConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public ErrorDecoder terceroErrorDecoder() {
        return (methodKey, response) -> {
            try {
                if (response.body() != null) {
                    String body = new String(response.body().asInputStream().readAllBytes());
                    ThirdErrorDto error = objectMapper.readValue(body, ThirdErrorDto.class);
                    return new TerceroApiException(
                        response.status(),
                        error.getCode(),
                        error.getMessage(),
                        error.getRetryable() != null && error.getRetryable(),
                        error.getCorrelationId(),
                        error.getTimestamp() != null ? error.getTimestamp().toString() : null,
                        error.getOriginalConfirmation()
                    );
                }
            } catch (IOException ignored) {
            }
            return new TerceroApiException(
                response.status(),
                "UNKNOWN",
                "Error sin cuerpo estructurado: " + response.reason(),
                isRetryableStatus(response.status()),
                null,
                java.time.Instant.now().toString(),
                null
            );
        };
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }
}