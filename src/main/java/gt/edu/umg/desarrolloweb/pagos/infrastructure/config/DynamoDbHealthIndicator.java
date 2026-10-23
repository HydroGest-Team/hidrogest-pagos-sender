package gt.edu.umg.desarrolloweb.pagos.infrastructure.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;

@Component
public class DynamoDbHealthIndicator implements HealthIndicator {

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbHealthIndicator(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Health health() {
        try {
            dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build());
            return Health.up().withDetail("dynamodb", "connected").build();
        } catch (Exception e) {
            return Health.down().withDetail("dynamodb", "disconnected").withException(e).build();
        }
    }
}