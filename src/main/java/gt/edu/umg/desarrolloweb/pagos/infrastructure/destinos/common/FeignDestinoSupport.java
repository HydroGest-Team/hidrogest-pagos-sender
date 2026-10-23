package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import feign.Request;
import feign.RequestInterceptor;
import gt.edu.umg.desarrolloweb.pagos.application.Correlation;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Piezas Feign comunes; cada destino las registra en su propia configuración (aislada de los demás). */
public final class FeignDestinoSupport {

    private FeignDestinoSupport() {
    }

    /** API key, correlation id y, solo si está configurado, el escenario de simulación. */
    public static RequestInterceptor headers(DestinosProperties.Http http) {
        return template -> {
            template.header("x-api-key", http.apiKey());
            String correlationId = MDC.get(Correlation.MDC_KEY);
            template.header(Correlation.HEADER, correlationId != null ? correlationId : UUID.randomUUID().toString());
            if (!http.simulationScenario().isBlank()) {
                template.header("X-Simulation-Scenario", http.simulationScenario());
            }
        };
    }

    public static Request.Options timeouts(DestinosProperties.Http http) {
        return new Request.Options(http.connectTimeout().toMillis(), TimeUnit.MILLISECONDS,
                http.readTimeout().toMillis(), TimeUnit.MILLISECONDS, false);
    }
}
