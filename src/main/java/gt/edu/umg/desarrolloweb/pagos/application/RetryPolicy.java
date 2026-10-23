package gt.edu.umg.desarrolloweb.pagos.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Elige la espera antes del próximo intento entre los tiers configurados (cada tier es una cola
 * con TTL). Backoff por número de intento, pero nunca menos que el {@code Retry-After} del destino.
 */
@Component
public class RetryPolicy {

    private final List<Duration> tiers;
    private final int maxIntentos;

    public RetryPolicy(PagosProperties properties) {
        this.tiers = properties.reintentos().tiers();
        this.maxIntentos = properties.reintentos().maxIntentos();
    }

    /** @param intentos intentos ya realizados (incluido el que acaba de fallar), desde 1 */
    public Duration delayFor(int intentos, Duration retryAfter) {
        Duration porIntento = tiers.get(Math.min(Math.max(intentos, 1) - 1, tiers.size() - 1));
        Duration minimo = retryAfter == null ? Duration.ZERO : retryAfter;
        return atLeast(porIntento.compareTo(minimo) >= 0 ? porIntento : minimo);
    }

    /** El tier más corto que sea {@code >= delay}; si ninguno alcanza, el mayor. */
    public Duration atLeast(Duration delay) {
        return tiers.stream()
                .filter(t -> t.compareTo(delay) >= 0)
                .findFirst()
                .orElse(tiers.get(tiers.size() - 1));
    }

    public boolean agotado(int intentos) {
        return intentos >= maxIntentos;
    }

    public List<Duration> tiers() {
        return tiers;
    }
}
