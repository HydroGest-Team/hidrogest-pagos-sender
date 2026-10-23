package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class MetricsService {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> deliveryCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordDelivery(String destino, String resultado) {
        String key = destino + "." + resultado;
        deliveryCounters.computeIfAbsent(key, k -> Counter.builder("pagos.delivery")
            .tag("destino", destino)
            .tag("resultado", resultado)
            .register(registry))
            .increment();
    }

    public void recordLatency(String destino, long durationMs) {
        Timer timer = latencyTimers.computeIfAbsent(destino, k -> Timer.builder("pagos.latency")
            .tag("destino", destino)
            .register(registry));
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordPendingCount(String destino, long count) {
        registry.gauge("pagos.pending", java.util.List.of(io.micrometer.core.instrument.Tag.of("destino", destino)), count);
    }

    public void recordDlqCount(String destino, long count) {
        registry.gauge("pagos.dlq", java.util.List.of(io.micrometer.core.instrument.Tag.of("destino", destino)), count);
    }
}