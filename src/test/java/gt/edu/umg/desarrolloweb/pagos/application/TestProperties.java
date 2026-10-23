package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.Destino;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Configuración equivalente a los valores por defecto de {@code application.yml}. */
public final class TestProperties {

    private TestProperties() {
    }

    public static PagosProperties pagos() {
        return pagos("", List.of(Destino.BANCO, Destino.ERP, Destino.TERCERO));
    }

    public static PagosProperties pagos(String prefijoLlave, List<Destino> activos) {
        return new PagosProperties("EQUIPO_A", prefijoLlave, ZoneId.of("America/Guatemala"), activos, Map.of(),
                new PagosProperties.Producer(Duration.ofSeconds(10), 200, Duration.ofMinutes(10)),
                new PagosProperties.Reintentos(6, List.of(Duration.ofSeconds(5), Duration.ofSeconds(30),
                        Duration.ofMinutes(2), Duration.ofMinutes(10))),
                new PagosProperties.Reconciliador(Duration.ofMinutes(1), Duration.ofSeconds(30),
                        Duration.ofMinutes(1), Duration.ofMinutes(10), 500));
    }
}
