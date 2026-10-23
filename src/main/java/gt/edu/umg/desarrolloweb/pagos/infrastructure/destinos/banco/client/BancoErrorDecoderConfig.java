package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankErrorDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class BancoErrorDecoderConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public ErrorDecoder bancoErrorDecoder() {
        return (methodKey, response) -> {
            try {
                if (response.body() != null) {
                    String body = new String(response.body().asInputStream().readAllBytes());
                    BankErrorDto error = objectMapper.readValue(body, BankErrorDto.class);
                    return new BancoApiException(
                        response.status(),
                        error.getCode(),
                        error.getMessage(),
                        error.getRetryable() != null && error.getRetryable(),
                        error.getCorrelationId(),
                        error.getTimestamp() != null ? error.getTimestamp().toString() : null,
                        error.getDetails(),
                        error.getOriginalReceipt()
                    );
                }
            } catch (IOException ignored) {
                // Body no es JSON del contrato (ej. error de API Gateway)
            }
            return new BancoApiException(
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