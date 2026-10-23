package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;

import java.util.List;

/**
 * Esquema de las 3 tablas.
 * <ul>
 *   <li>{@code pagos_entregas}: PK {@code paymentId} (N), SK {@code destino} (S). GSI disperso
 *       {@code abiertos-index} ({@code openKey}, {@code nextAttemptAt} N): solo contiene estados no terminales.</li>
 *   <li>{@code pagos_intentos}: PK {@code pk} = {@code paymentId#DESTINO}, SK {@code sk} = {@code epochMillis#intento}; TTL {@code expiresAt}.</li>
 *   <li>{@code pagos_control}: PK {@code pk} (S).</li>
 * </ul>
 */
public class DynamoDbTables {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTables.class);

    private final DynamoDbClient client;
    private final DynamoDbProperties properties;

    public DynamoDbTables(DynamoDbClient client, DynamoDbProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public String entregas() {
        return properties.tablaEntregas();
    }

    public String intentos() {
        return properties.tablaIntentos();
    }

    public String control() {
        return properties.tablaControl();
    }

    void createIfMissing() {
        create(CreateTableRequest.builder()
                .tableName(entregas())
                .keySchema(key("paymentId", KeyType.HASH), key("destino", KeyType.RANGE))
                .attributeDefinitions(
                        attr("paymentId", ScalarAttributeType.N),
                        attr("destino", ScalarAttributeType.S),
                        attr("openKey", ScalarAttributeType.S),
                        attr("nextAttemptAt", ScalarAttributeType.N))
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName(DynamoDbProperties.GSI_ABIERTOS)
                        .keySchema(key("openKey", KeyType.HASH), key("nextAttemptAt", KeyType.RANGE))
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
        boolean creada = create(CreateTableRequest.builder()
                .tableName(intentos())
                .keySchema(key("pk", KeyType.HASH), key("sk", KeyType.RANGE))
                .attributeDefinitions(attr("pk", ScalarAttributeType.S), attr("sk", ScalarAttributeType.S))
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
        if (creada) {
            client.updateTimeToLive(r -> r.tableName(intentos())
                    .timeToLiveSpecification(TimeToLiveSpecification.builder().attributeName("expiresAt").enabled(true).build()));
        }
        create(CreateTableRequest.builder()
                .tableName(control())
                .keySchema(key("pk", KeyType.HASH))
                .attributeDefinitions(attr("pk", ScalarAttributeType.S))
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    private boolean create(CreateTableRequest request) {
        try {
            client.describeTable(r -> r.tableName(request.tableName()));
            return false;
        } catch (ResourceNotFoundException e) {
            client.createTable(request);
            client.waiter().waitUntilTableExists(r -> r.tableName(request.tableName()));
            log.info("Tabla DynamoDB creada: {}", request.tableName());
            return true;
        }
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private static AttributeDefinition attr(String name, ScalarAttributeType type) {
        return AttributeDefinition.builder().attributeName(name).attributeType(type).build();
    }

    List<String> all() {
        return List.of(entregas(), intentos(), control());
    }
}
