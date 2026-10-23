package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TerceroClientConfig {

    @Value("${tercero.api-key}")
    private String apiKey;

    @Value("${tercero.simulation-scenario:}")
    private String simulationScenario;

    @Bean
    public RequestInterceptor terceroRequestInterceptor() {
        return template -> {
            template.header("x-api-key", apiKey);
            template.header("X-Correlation-Id", correlationId());
            if (simulationScenario != null && !simulationScenario.isBlank()) {
                template.header("X-Simulation-Scenario", simulationScenario);
            }
        };
    }

    private String correlationId() {
        return org.slf4j.MDC.get("X-Correlation-Id") != null 
            ? org.slf4j.MDC.get("X-Correlation-Id") 
            : java.util.UUID.randomUUID().toString();
    }
}