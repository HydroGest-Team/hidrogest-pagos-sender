package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.gateway.BancoGateway;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class BancoHealthIndicator implements HealthIndicator {

    private final BancoGateway bancoGateway;

    public BancoHealthIndicator(BancoGateway bancoGateway) {
        this.bancoGateway = bancoGateway;
    }

    @Override
    public Health health() {
        try {
            boolean up = bancoGateway.health();
            return up ? Health.up().withDetail("banco", "up").build() : Health.down().withDetail("banco", "down").build();
        } catch (Exception e) {
            return Health.down().withDetail("banco", "down").withException(e).build();
        }
    }
}