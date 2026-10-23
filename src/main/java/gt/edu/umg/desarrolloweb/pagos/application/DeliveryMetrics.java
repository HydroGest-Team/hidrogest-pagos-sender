package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Métricas de negocio publicadas en {@code /actuator/metrics} y {@code /actuator/prometheus}. */
@Component
public class DeliveryMetrics {

    private final MeterRegistry registry;
    private final Counter pagosCompletos;
    private final Map<Destino, AtomicLong> abiertos = new EnumMap<>(Destino.class);

    public DeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.pagosCompletos = Counter.builder("pagos.completos")
                .description("Pagos entregados a todos los destinos activos")
                .register(registry);
        for (Destino destino : Destino.values()) {
            AtomicLong valor = new AtomicLong();
            abiertos.put(destino, valor);
            Gauge.builder("pagos.entregas.abiertas", valor, AtomicLong::get)
                    .description("Entregas no terminales (pendientes, en vuelo, en reintento o desconocidas)")
                    .tag("destino", destino.name())
                    .register(registry);
        }
    }

    public void intento(Destino destino, String operacion, Outcome outcome, Duration duracion) {
        Counter.builder("pagos.intentos")
                .tag("destino", destino.name())
                .tag("operacion", operacion)
                .tag("resultado", outcome.name())
                .register(registry)
                .increment();
        Timer.builder("pagos.intentos.latencia")
                .tag("destino", destino.name())
                .tag("operacion", operacion)
                .register(registry)
                .record(duracion);
    }

    public void entregaCerrada(Destino destino, String estadoFinal) {
        Counter.builder("pagos.entregas.cerradas")
                .tag("destino", destino.name())
                .tag("estado", estadoFinal)
                .register(registry)
                .increment();
    }

    public void pagoCompleto() {
        pagosCompletos.increment();
    }

    public void abiertos(Map<Destino, Long> conteo) {
        abiertos.forEach((destino, valor) -> valor.set(conteo.getOrDefault(destino, 0L)));
    }
}
