package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "pagos")
@Validated
public class PagosProperties {

    private String teamCode = "EQUIPO_A";
    private List<String> destinosActivos = List.of("BANCO", "ERP", "TERCERO");
    private String terceroDesde;

    private Producer producer = new Producer();
    private Reintentos reintentos = new Reintentos();
    private Reconciliador reconciliador = new Reconciliador();

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public List<String> getDestinosActivos() { return destinosActivos; }
    public void setDestinosActivos(List<String> destinosActivos) { this.destinosActivos = destinosActivos; }
    public String getTerceroDesde() { return terceroDesde; }
    public void setTerceroDesde(String terceroDesde) { this.terceroDesde = terceroDesde; }

    // Alias for backward compatibility
    public List<String> getActivos() { return destinosActivos; }
    public void setActivos(List<String> activos) { this.destinosActivos = activos; }

    public String prefijo() {
        if (teamCode == null || teamCode.isBlank()) return "EQA-";
        return teamCode.substring(0, 3).toUpperCase() + "-";
    }

    public Producer getProducer() { return producer; }
    public void setProducer(Producer producer) { this.producer = producer; }
    public Reintentos getReintentos() { return reintentos; }
    public void setReintentos(Reintentos reintentos) { this.reintentos = reintentos; }
    public Reconciliador getReconciliador() { return reconciliador; }
    public void setReconciliador(Reconciliador reconciliador) { this.reconciliador = reconciliador; }

    @Validated
    public static class Producer {
        @NotNull private Duration intervalo = Duration.ofSeconds(10);
        private int lote = 200;
        @NotNull private Duration traslape = Duration.ofMinutes(10);

        public Duration getIntervalo() { return intervalo; }
        public void setIntervalo(Duration intervalo) { this.intervalo = intervalo; }
        public int getLote() { return lote; }
        public void setLote(int lote) { this.lote = lote; }
        public Duration getTraslape() { return traslape; }
        public void setTraslape(Duration traslape) { this.traslape = traslape; }
    }

    @Validated
    public static class Reintentos {
        private int maxIntentos = 6;
        private List<String> tiers = List.of("5s", "30s", "2m", "10m");

        public int getMaxIntentos() { return maxIntentos; }
        public void setMaxIntentos(int maxIntentos) { this.maxIntentos = maxIntentos; }
        public List<String> getTiers() { return tiers; }
        public void setTiers(List<String> tiers) { this.tiers = tiers; }
    }

    @Validated
    public static class Reconciliador {
        @NotNull private Duration intervalo = Duration.ofMinutes(2);
        @NotNull private Duration lease = Duration.ofSeconds(30);
        @NotNull private Duration pendienteSinActividad = Duration.ofMinutes(5);

        public Duration getIntervalo() { return intervalo; }
        public void setIntervalo(Duration intervalo) { this.intervalo = intervalo; }
        public Duration getLease() { return lease; }
        public void setLease(Duration lease) { this.lease = lease; }
        public Duration getPendienteSinActividad() { return pendienteSinActividad; }
        public void setPendienteSinActividad(Duration pendienteSinActividad) { this.pendienteSinActividad = pendienteSinActividad; }
    }
}