package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.config.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;

@Configuration
public class DynamoDbConfig {

    private final AwsProperties awsProperties;

    public DynamoDbConfig(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
            .region(Region.of(awsProperties.getRegion()));

        if (awsProperties.getDynamodbEndpoint() != null && !awsProperties.getDynamodbEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(awsProperties.getDynamodbEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    }

    @Bean
    @Profile("dev")
    public DynamoDbTableInitializer tableInitializer(DynamoDbClient dynamoDbClient) {
        return new DynamoDbTableInitializer(dynamoDbClient);
    }

    public static class DynamoDbTableInitializer {
        private final DynamoDbClient dynamoDbClient;

        public DynamoDbTableInitializer(DynamoDbClient dynamoDbClient) {
            this.dynamoDbClient = dynamoDbClient;
            initTables();
        }

        private void initTables() {
            createTableIfNotExists("pagos_entregas", List.of(
                KeySchemaElement.builder().attributeName("paymentId").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("destino").keyType(KeyType.RANGE).build()
            ), List.of(
                AttributeDefinition.builder().attributeName("paymentId").attributeType(ScalarAttributeType.N).build(),
                AttributeDefinition.builder().attributeName("destino").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("openKey").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("nextAttemptAt").attributeType(ScalarAttributeType.S).build()
            ), List.of(
                GlobalSecondaryIndex.builder()
                    .indexName("abiertos-index")
                    .keySchema(
                        KeySchemaElement.builder().attributeName("openKey").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("nextAttemptAt").keyType(KeyType.RANGE).build()
                    )
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .build()
            ));

            createTableIfNotExists("pagos_intentos", List.of(
                KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
            ), List.of(
                AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build()
            ), List.of());

            createTableIfNotExists("pagos_control", List.of(
                KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build()
            ), List.of(
                AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build()
            ), List.of());
        }

        private void createTableIfNotExists(String tableName, List<KeySchemaElement> keySchema,
                                             List<AttributeDefinition> attributeDefinitions,
                                             List<GlobalSecondaryIndex> globalSecondaryIndexes) {
            try {
                dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
            } catch (ResourceNotFoundException e) {
                CreateTableRequest.Builder builder = CreateTableRequest.builder()
                    .tableName(tableName)
                    .keySchema(keySchema)
                    .attributeDefinitions(attributeDefinitions)
                    .billingMode(BillingMode.PAY_PER_REQUEST);

                if (!globalSecondaryIndexes.isEmpty()) {
                    builder.globalSecondaryIndexes(globalSecondaryIndexes);
                }

                dynamoDbClient.createTable(builder.build());
                dynamoDbClient.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(tableName).build());
            }
        }
    }
}