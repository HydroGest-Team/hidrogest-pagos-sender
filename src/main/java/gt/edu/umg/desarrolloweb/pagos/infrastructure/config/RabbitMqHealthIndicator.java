package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqHealthIndicator implements HealthIndicator {

    private final ConnectionFactory connectionFactory;

    public RabbitMqHealthIndicator(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            connectionFactory.createConnection().close();
            return Health.up().withDetail("rabbitmq", "connected").build();
        } catch (Exception e) {
            return Health.down().withDetail("rabbitmq", "disconnected").withException(e).build();
        }
    }
}