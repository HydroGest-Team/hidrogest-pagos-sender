package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.gateway.TerceroGateway;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class TerceroHealthIndicator implements HealthIndicator {

    private final TerceroGateway terceroGateway;

    public TerceroHealthIndicator(TerceroGateway terceroGateway) {
        this.terceroGateway = terceroGateway;
    }

    @Override
    public Health health() {
        try {
            boolean up = terceroGateway.health();
            return up ? Health.up().withDetail("tercero", "up").build() : Health.down().withDetail("tercero", "down").build();
        } catch (Exception e) {
            return Health.down().withDetail("tercero", "down").withException(e).build();
        }
    }
}