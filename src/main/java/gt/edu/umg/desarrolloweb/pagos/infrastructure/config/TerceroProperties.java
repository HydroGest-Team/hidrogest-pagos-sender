package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "tercero")
@Validated
public class TerceroProperties {
    @NotBlank
    private String baseUrl = "http://localhost:8089";
    private String apiKey = "dev-key";
    private String simulationScenario;
    private int connectTimeout = 2000;
    private int readTimeout = 3000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getSimulationScenario() { return simulationScenario; }
    public void setSimulationScenario(String simulationScenario) { this.simulationScenario = simulationScenario; }
    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
}