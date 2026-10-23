package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Conexión a cada destino ({@code destinos.banco.*}, {@code destinos.erp.*}, {@code destinos.tercero.*}). */
@Validated
@ConfigurationProperties(prefix = "destinos")
public record DestinosProperties(
        @Valid @NotNull Http banco,
        @Valid @NotNull Http erp,
        @Valid @NotNull Http tercero
) {
    /**
     * @param apiKey             se valida al arrancar solo si el destino está activo
     * @param simulationScenario valor del header {@code X-Simulation-Scenario}; vacío = no se envía
     */
    public record Http(
            @NotBlank String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String simulationScenario,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("3s") Duration readTimeout
    ) {
        @Override
        public String toString() {
            // Nunca imprimir la API key (por ejemplo, en errores de binding).
            return "Http[baseUrl=" + baseUrl + ", apiKey=" + (apiKey.isBlank() ? "<vacía>" : "<oculta>")
                    + ", simulationScenario=" + simulationScenario + ", connectTimeout=" + connectTimeout
                    + ", readTimeout=" + readTimeout + "]";
        }
    }
}
