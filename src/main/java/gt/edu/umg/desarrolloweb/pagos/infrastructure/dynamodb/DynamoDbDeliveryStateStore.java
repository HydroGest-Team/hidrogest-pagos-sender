package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.EstadoEntrega;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estado por (pago, destino) en {@code pagos_entregas}. Cada transición es un {@code UpdateItem}
 * condicional; {@code openKey} existe solo mientras el estado está abierto, así el índice
 * {@code abiertos-index} contiene únicamente lo pendiente.
 */
@Repository
public class DynamoDbDeliveryStateStore implements DeliveryStateStore {

    private static final String OPEN = "OPEN";
    private static final int MAX_DETALLE = 500;
    private static final int BATCH_GET = 100;

    private final DynamoDbClient client;
    private final String table;

    public DynamoDbDeliveryStateStore(DynamoDbClient client, DynamoDbTables tables) {
        this.client = client;
        this.table = tables.entregas();
    }

    @Override
    public boolean createPending(long paymentId, Destino destino, String idempotencyKey, Instant now) {
        Map<String, AttributeValue> item = new HashMap<>(key(paymentId, destino));
        item.put("estado", Attr.s(EstadoEntrega.PENDIENTE.name()));
        item.put("intentos", Attr.n(0));
        item.put("idempotencyKey", Attr.s(idempotencyKey));
        item.put("nextAttemptAt", Attr.millis(now));
        item.put("openKey", Attr.s(OPEN));
        item.put("createdAt", Attr.iso(now));
        item.put("updatedAt", Attr.iso(now));
        item.put("version", Attr.n(0));
        try {
            client.putItem(r -> r.tableName(table).item(item).conditionExpression("attribute_not_exists(paymentId)"));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public Set<Key> existing(Collection<Long> paymentIds, Collection<Destino> destinos) {
        List<Map<String, AttributeValue>> keys = new ArrayList<>();
        for (Long id : new HashSet<>(paymentIds)) {
            for (Destino d : destinos) {
                keys.add(key(id, d));
            }
        }
        Set<Key> found = new HashSet<>();
        for (int i = 0; i < keys.size(); i += BATCH_GET) {
            Map<String, KeysAndAttributes> pending = Map.of(table, KeysAndAttributes.builder()
                    .keys(keys.subList(i, Math.min(i + BATCH_GET, keys.size())))
                    .projectionExpression("paymentId, destino")
                    .consistentRead(true)
                    .build());
            while (!pending.isEmpty()) {
                Map<String, KeysAndAttributes> request = pending;
                BatchGetItemResponse response = client.batchGetItem(r -> r.requestItems(request));
                response.responses().getOrDefault(table, List.of())
                        .forEach(item -> found.add(new Key(Attr.lng(item, "paymentId"), Destino.valueOf(Attr.str(item, "destino")))));
                pending = response.unprocessedKeys();
            }
        }
        return found;
    }

    @Override
    public Optional<DeliveryState> find(long paymentId, Destino destino) {
        var response = client.getItem(r -> r.tableName(table).key(key(paymentId, destino)).consistentRead(true));
        return response.hasItem() && !response.item().isEmpty() ? Optional.of(fromItem(response.item())) : Optional.empty();
    }

    @Override
    public List<DeliveryState> findByPayment(long paymentId) {
        return client.queryPaginator(QueryRequest.builder()
                        .tableName(table)
                        .keyConditionExpression("paymentId = :id")
                        .expressionAttributeValues(Map.of(":id", Attr.n(paymentId)))
                        .consistentRead(true)
                        .build())
                .items().stream().map(DynamoDbDeliveryStateStore::fromItem).toList();
    }

    @Override
    public Optional<DeliveryState> claim(long paymentId, Destino destino, Instant now, Instant leaseUntil) {
        UpdateBuilder u = new UpdateBuilder()
                .set("estado", Attr.s(EstadoEntrega.EN_VUELO.name()))
                .set("leaseUntil", Attr.millis(leaseUntil))
                // Si el consumer muere, el reconciliador lo encuentra cuando venza el lease.
                .set("nextAttemptAt", Attr.millis(leaseUntil))
                .set("updatedAt", Attr.iso(now))
                .value(":uno", Attr.n(1))
                .value(":pendiente", Attr.s(EstadoEntrega.PENDIENTE.name()))
                .value(":reintento", Attr.s(EstadoEntrega.REINTENTO.name()))
                .value(":desconocido", Attr.s(EstadoEntrega.DESCONOCIDO.name()))
                .value(":enVuelo", Attr.s(EstadoEntrega.EN_VUELO.name()))
                .value(":now", Attr.millis(now));
        String estado = u.name("estado");
        String version = u.name("version");
        String lease = u.name("leaseUntil");
        u.condition(estado + " IN (:pendiente, :reintento, :desconocido) OR ("
                + estado + " = :enVuelo AND " + lease + " < :now)");
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(table)
                .key(key(paymentId, destino))
                .updateExpression(u.expression() + ", " + version + " = " + version + " + :uno")
                .conditionExpression(u.conditionExpression())
                .expressionAttributeNames(u.names())
                .expressionAttributeValues(u.values())
                .returnValues(ReturnValue.ALL_OLD)
                .build();
        try {
            UpdateItemResponse response = client.updateItem(request);
            return Optional.of(fromItem(response.attributes()));
        } catch (ConditionalCheckFailedException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean markDelivered(long paymentId, Destino destino, long expectedVersion, DeliveryResult result, Instant now) {
        UpdateBuilder u = withResult(new UpdateBuilder(), result, now)
                .set("estado", Attr.s(EstadoEntrega.ENTREGADO.name()))
                .set("receiptId", Attr.s(result.receiptId()))
                .remove("openKey").remove("leaseUntil").remove("nextAttemptAt");
        return conditionalOnVersion(paymentId, destino, expectedVersion, u);
    }

    @Override
    public boolean markRetry(long paymentId, Destino destino, long expectedVersion, EstadoEntrega estado,
                             int intentos, Instant nextAttemptAt, DeliveryResult result, Instant now) {
        UpdateBuilder u = withResult(new UpdateBuilder(), result, now)
                .set("estado", Attr.s(estado.name()))
                .set("intentos", Attr.n(intentos))
                .set("nextAttemptAt", Attr.millis(nextAttemptAt))
                .remove("leaseUntil");
        return conditionalOnVersion(paymentId, destino, expectedVersion, u);
    }

    @Override
    public boolean markDeadLetter(long paymentId, Destino destino, long expectedVersion, int intentos,
                                  DeliveryResult result, Instant now) {
        UpdateBuilder u = withResult(new UpdateBuilder(), result, now)
                .set("estado", Attr.s(EstadoEntrega.DEAD_LETTER.name()))
                .set("intentos", Attr.n(intentos))
                .remove("openKey").remove("leaseUntil").remove("nextAttemptAt");
        return conditionalOnVersion(paymentId, destino, expectedVersion, u);
    }

    @Override
    public boolean resetDeadLetter(long paymentId, Destino destino, Instant now) {
        UpdateBuilder u = new UpdateBuilder()
                .set("estado", Attr.s(EstadoEntrega.PENDIENTE.name()))
                .set("intentos", Attr.n(0))
                .set("nextAttemptAt", Attr.millis(now))
                .set("openKey", Attr.s(OPEN))
                .set("updatedAt", Attr.iso(now))
                .value(":uno", Attr.n(1))
                .value(":dl", Attr.s(EstadoEntrega.DEAD_LETTER.name()));
        String version = u.name("version");
        u.condition(u.name("estado") + " = :dl");
        return update(paymentId, destino, u, ", " + version + " = " + version + " + :uno");
    }

    @Override
    public List<DeliveryState> findOverdue(Instant before, int limit) {
        List<DeliveryState> result = new ArrayList<>();
        var pages = client.queryPaginator(QueryRequest.builder()
                .tableName(table)
                .indexName(DynamoDbProperties.GSI_ABIERTOS)
                .keyConditionExpression("openKey = :open AND nextAttemptAt <= :before")
                .expressionAttributeValues(Map.of(":open", Attr.s(OPEN), ":before", Attr.millis(before)))
                .limit(limit)
                .build());
        for (Map<String, AttributeValue> item : pages.items()) {
            result.add(fromItem(item));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public Map<Destino, Long> countOpen() {
        Map<Destino, Long> counts = new EnumMap<>(Destino.class);
        client.queryPaginator(QueryRequest.builder()
                        .tableName(table)
                        .indexName(DynamoDbProperties.GSI_ABIERTOS)
                        .keyConditionExpression("openKey = :open")
                        .expressionAttributeValues(Map.of(":open", Attr.s(OPEN)))
                        .projectionExpression("destino")
                        .build())
                .items()
                .forEach(item -> counts.merge(Destino.valueOf(Attr.str(item, "destino")), 1L, Long::sum));
        return counts;
    }

    private UpdateBuilder withResult(UpdateBuilder u, DeliveryResult result, Instant now) {
        return u.set("lastHttpStatus", Attr.n(result.httpStatus()))
                .set("lastErrorCode", Attr.s(result.errorCode()))
                .set("lastErrorDetail", Attr.s(Attr.truncate(result.detail(), MAX_DETALLE)))
                .set("updatedAt", Attr.iso(now));
    }

    private boolean conditionalOnVersion(long paymentId, Destino destino, long expectedVersion, UpdateBuilder u) {
        u.set("version", Attr.n(expectedVersion + 1))
                .value(":expected", Attr.n(expectedVersion));
        u.condition(u.name("version") + " = :expected");
        return update(paymentId, destino, u, "");
    }

    private boolean update(long paymentId, Destino destino, UpdateBuilder u, String extraSet) {
        String expression = u.expression();
        if (!extraSet.isEmpty()) {
            // extraSet agrega asignaciones a la cláusula SET (por ejemplo "version = version + :uno").
            int remove = expression.indexOf(" REMOVE ");
            expression = remove < 0 ? expression + extraSet
                    : expression.substring(0, remove) + extraSet + expression.substring(remove);
        }
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(table)
                .key(key(paymentId, destino))
                .updateExpression(expression)
                .conditionExpression(u.conditionExpression())
                .expressionAttributeNames(u.names())
                .expressionAttributeValues(u.values())
                .build();
        try {
            client.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private static Map<String, AttributeValue> key(long paymentId, Destino destino) {
        return Map.of("paymentId", Attr.n(paymentId), "destino", Attr.s(destino.name()));
    }

    static DeliveryState fromItem(Map<String, AttributeValue> item) {
        return new DeliveryState(
                Attr.lng(item, "paymentId"),
                Destino.valueOf(Attr.str(item, "destino")),
                EstadoEntrega.valueOf(Attr.str(item, "estado")),
                Attr.integer(item, "intentos"),
                Attr.fromMillis(item, "nextAttemptAt"),
                Attr.fromMillis(item, "leaseUntil"),
                Attr.str(item, "idempotencyKey"),
                Attr.str(item, "receiptId"),
                Attr.integer(item, "lastHttpStatus"),
                Attr.str(item, "lastErrorCode"),
                Attr.str(item, "lastErrorDetail"),
                Attr.fromIso(item, "createdAt"),
                Attr.fromIso(item, "updatedAt"),
                Attr.lng(item, "version"));
    }
}
