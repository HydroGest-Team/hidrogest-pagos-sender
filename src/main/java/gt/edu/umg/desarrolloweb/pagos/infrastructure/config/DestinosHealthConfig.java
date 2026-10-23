package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import gt.edu.umg.desarrolloweb.pagos.application.DestinationRegistry;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common.HttpDestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb.DynamoDbTables;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class DestinosHealthConfig {

    /**
     * {@code /actuator/health/destinos}: salud de cada API externa y estado de su circuit breaker.
     * Va en un grupo aparte: que el banco esté caído no debe reiniciar ni sacar de servicio a este proceso.
     */
    @Bean
    public HealthContributor destinosHealthContributor(DestinationRegistry registry) {
        Map<String, HealthIndicator> indicators = new LinkedHashMap<>();
        for (var destino : registry.activos()) {
            registry.gateway(destino).ifPresent(gateway -> indicators.put(destino.slug(), () -> {
                Health.Builder health = gateway.healthy() ? Health.up() : Health.down();
                if (gateway instanceof HttpDestinationGateway http) {
                    health.withDetail("circuitBreaker", http.circuitState().name());
                }
                return health.build();
            }));
        }
        return CompositeHealthContributor.fromMap(indicators);
    }

    @Bean
    public HealthIndicator dynamoDbHealthIndicator(DynamoDbClient client, DynamoDbTables tables) {
        return () -> {
            try {
                var status = client.describeTable(r -> r.tableName(tables.entregas())).table().tableStatusAsString();
                return Health.up().withDetail("tabla", tables.entregas()).withDetail("estado", status).build();
            } catch (RuntimeException e) {
                return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
            }
        };
    }
}
