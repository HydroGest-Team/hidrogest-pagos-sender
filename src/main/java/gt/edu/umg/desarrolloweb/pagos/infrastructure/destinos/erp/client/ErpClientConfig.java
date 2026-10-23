package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ErpClientConfig {

    @Value("${erp.api-key}")
    private String apiKey;

    @Value("${erp.simulation-scenario:}")
    private String simulationScenario;

    @Bean
    public RequestInterceptor erpRequestInterceptor() {
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