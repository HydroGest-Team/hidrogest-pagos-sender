package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.gateway.ErpGateway;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ErpHealthIndicator implements HealthIndicator {

    private final ErpGateway erpGateway;

    public ErpHealthIndicator(ErpGateway erpGateway) {
        this.erpGateway = erpGateway;
    }

    @Override
    public Health health() {
        try {
            boolean up = erpGateway.health();
            return up ? Health.up().withDetail("erp", "up").build() : Health.down().withDetail("erp", "down").build();
        } catch (Exception e) {
            return Health.down().withDetail("erp", "down").withException(e).build();
        }
    }
}