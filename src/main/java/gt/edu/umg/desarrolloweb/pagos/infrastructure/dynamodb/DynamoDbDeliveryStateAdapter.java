package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DynamoDbDeliveryStateAdapter implements DeliveryStateStore {

    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE_NAME = "pagos_entregas";

    public DynamoDbDeliveryStateAdapter(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public boolean createIfNotExists(DeliveryState state) {
        state.setCreatedAt(Instant.now());
        state.setUpdatedAt(Instant.now());
        state.setVersion(0L);
        state.setOpenKey(state.isAbierto() ? "OPEN" : null);

        Map<String, AttributeValue> item = toItem(state);
        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(item)
            .conditionExpression("attribute_not_exists(paymentId) AND attribute_not_exists(destino)")
            .build();

        try {
            dynamoDbClient.putItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public Optional<DeliveryState> findByPaymentIdAndDestino(Long paymentId, String destino) {
        Map<String, AttributeValue> key = Map.of(
            "paymentId", AttributeValue.builder().n(paymentId.toString()).build(),
            "destino", AttributeValue.builder().s(destino).build()
        );

        GetItemRequest request = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(key)
            .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        if (response.hasItem()) {
            return Optional.of(fromItem(response.item()));
        }
        return Optional.empty();
    }

    @Override
    public boolean claimForProcessing(Long paymentId, String destino, Instant leaseUntil) {
        Instant now = Instant.now();
        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "paymentId", AttributeValue.builder().n(paymentId.toString()).build(),
                "destino", AttributeValue.builder().s(destino).build()
            ))
            .updateExpression("SET #estado = :enVuelo, leaseUntil = :lease, updatedAt = :now, version = version + :inc")
            .conditionExpression("#estado IN (:pendiente, :reintento, :desconocido) OR (#estado = :enVuelo AND leaseUntil < :now)")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(Map.of(
                ":enVuelo", AttributeValue.builder().s("EN_VUELO").build(),
                ":pendiente", AttributeValue.builder().s("PENDIENTE").build(),
                ":reintento", AttributeValue.builder().s("REINTENTO").build(),
                ":desconocido", AttributeValue.builder().s("DESCONOCIDO").build(),
                ":lease", AttributeValue.builder().s(leaseUntil.toString()).build(),
                ":now", AttributeValue.builder().s(now.toString()).build(),
                ":inc", AttributeValue.builder().n("1").build()
            ))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        try {
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean updateStateConditionally(DeliveryState expected, DeliveryState updated) {
        updated.setUpdatedAt(Instant.now());
        updated.setVersion(expected.getVersion() + 1);
        updated.setOpenKey(updated.isAbierto() ? "OPEN" : null);

        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "paymentId", AttributeValue.builder().n(expected.getPaymentId().toString()).build(),
                "destino", AttributeValue.builder().s(expected.getDestino()).build()
            ))
            .updateExpression("SET #estado = :estado, receiptId = :receipt, updatedAt = :now, version = :version, openKey = :openKey, lastHttpStatus = :status, lastErrorCode = :code, nextAttemptAt = :nextAttempt, leaseUntil = :lease")
            .conditionExpression("version = :expectedVersion")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(Map.of(
                ":estado", AttributeValue.builder().s(updated.getEstado()).build(),
                ":receipt", AttributeValue.builder().s(updated.getReceiptId() != null ? updated.getReceiptId() : "").build(),
                ":now", AttributeValue.builder().s(updated.getUpdatedAt().toString()).build(),
                ":version", AttributeValue.builder().n(updated.getVersion().toString()).build(),
                ":openKey", AttributeValue.builder().s(updated.getOpenKey() != null ? updated.getOpenKey() : "").build(),
                ":status", AttributeValue.builder().n(updated.getLastHttpStatus() != null ? updated.getLastHttpStatus().toString() : "0").build(),
                ":code", AttributeValue.builder().s(updated.getLastErrorCode() != null ? updated.getLastErrorCode() : "").build(),
                ":nextAttempt", AttributeValue.builder().s(updated.getNextAttemptAt() != null ? updated.getNextAttemptAt().toString() : "").build(),
                ":lease", AttributeValue.builder().s(updated.getLeaseUntil() != null ? updated.getLeaseUntil().toString() : "").build(),
                ":expectedVersion", AttributeValue.builder().n(expected.getVersion().toString()).build()
            ))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        try {
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean markDelivered(Long paymentId, String destino, String receiptId) {
        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "paymentId", AttributeValue.builder().n(paymentId.toString()).build(),
                "destino", AttributeValue.builder().s(destino).build()
            ))
            .updateExpression("SET #estado = :entregado, receiptId = :receipt, updatedAt = :now, version = version + :inc, openKey = :null")
            .conditionExpression("#estado IN (:pendiente, :enVuelo, :reintento, :desconocido)")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(Map.of(
                ":entregado", AttributeValue.builder().s("ENTREGADO").build(),
                ":receipt", AttributeValue.builder().s(receiptId).build(),
                ":now", AttributeValue.builder().s(Instant.now().toString()).build(),
                ":inc", AttributeValue.builder().n("1").build(),
                ":null", AttributeValue.builder().s("").build(),
                ":pendiente", AttributeValue.builder().s("PENDIENTE").build(),
                ":enVuelo", AttributeValue.builder().s("EN_VUELO").build(),
                ":reintento", AttributeValue.builder().s("REINTENTO").build(),
                ":desconocido", AttributeValue.builder().s("DESCONOCIDO").build()
            ))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        try {
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean markDeadLetter(Long paymentId, String destino, int httpStatus, String errorCode) {
        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "paymentId", AttributeValue.builder().n(paymentId.toString()).build(),
                "destino", AttributeValue.builder().s(destino).build()
            ))
            .updateExpression("SET #estado = :dl, lastHttpStatus = :status, lastErrorCode = :code, updatedAt = :now, version = version + :inc, openKey = :null")
            .conditionExpression("#estado IN (:pendiente, :enVuelo, :reintento, :desconocido)")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(vals(
                ":dl", "DEAD_LETTER",
                ":status", String.valueOf(httpStatus),
                ":code", errorCode,
                ":now", Instant.now().toString(),
                ":inc", "1",
                ":null", "",
                ":pendiente", "PENDIENTE",
                ":enVuelo", "EN_VUELO",
                ":reintento", "REINTENTO",
                ":desconocido", "DESCONOCIDO"
            ))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        try {
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean updateForRetry(Long paymentId, String destino, Instant nextAttemptAt, int httpStatus, String errorCode) {
        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "paymentId", AttributeValue.builder().n(paymentId.toString()).build(),
                "destino", AttributeValue.builder().s(destino).build()
            ))
            .updateExpression("SET #estado = :reintento, nextAttemptAt = :next, lastHttpStatus = :status, lastErrorCode = :code, updatedAt = :now, version = version + :inc, leaseUntil = :null")
            .conditionExpression("#estado IN (:pendiente, :enVuelo, :reintento, :desconocido)")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(vals(
                ":reintento", "REINTENTO",
                ":next", nextAttemptAt.toString(),
                ":status", "n:" + httpStatus,
                ":code", errorCode,
                ":now", Instant.now().toString(),
                ":inc", "n:1",
                ":null", "",
                ":pendiente", "PENDIENTE",
                ":enVuelo", "EN_VUELO",
                ":reintento", "REINTENTO",
                ":desconocido", "DESCONOCIDO"
            ))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        try {
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public boolean updateForUnknown(Long paymentId, String destino) {
        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "paymentId", AttributeValue.builder().n(paymentId.toString()).build(),
                "destino", AttributeValue.builder().s(destino).build()
            ))
            .updateExpression("SET #estado = :desconocido, updatedAt = :now, version = version + :inc")
            .conditionExpression("#estado IN (:pendiente, :enVuelo, :reintento)")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(vals(
                ":desconocido", "DESCONOCIDO",
                ":now", Instant.now().toString(),
                ":inc", "n:1",
                ":pendiente", "PENDIENTE",
                ":enVuelo", "EN_VUELO",
                ":reintento", "REINTENTO"
            ))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        try {
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public List<DeliveryState> findAbiertosConNextAttemptAntesDe(Instant now) {
        QueryRequest request = QueryRequest.builder()
            .tableName(TABLE_NAME)
            .indexName("abiertos-index")
            .keyConditionExpression("openKey = :openKey AND nextAttemptAt <= :now")
            .expressionAttributeValues(Map.of(
                ":openKey", AttributeValue.builder().s("OPEN").build(),
                ":now", AttributeValue.builder().s(now.toString()).build()
            ))
            .build();

        List<DeliveryState> results = new ArrayList<>();
        SdkIterable<QueryResponse> pages = dynamoDbClient.queryPaginator(request);
        for (QueryResponse page : pages) {
            for (Map<String, AttributeValue> item : page.items()) {
                results.add(fromItem(item));
            }
        }
        return results;
    }

    @Override
    public List<DeliveryState> findEnVueloConLeaseVencido(Instant now) {
        ScanRequest request = ScanRequest.builder()
            .tableName(TABLE_NAME)
            .filterExpression("leaseUntil < :now AND #estado = :enVuelo")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(Map.of(
                ":now", AttributeValue.builder().s(now.toString()).build(),
                ":enVuelo", AttributeValue.builder().s("EN_VUELO").build()
            ))
            .build();

        List<DeliveryState> results = new ArrayList<>();
        SdkIterable<ScanResponse> pages = dynamoDbClient.scanPaginator(request);
        for (ScanResponse page : pages) {
            for (Map<String, AttributeValue> item : page.items()) {
                results.add(fromItem(item));
            }
        }
        return results;
    }

    @Override
    public List<DeliveryState> findPendientesSinActividadDesde(Instant since) {
        ScanRequest request = ScanRequest.builder()
            .tableName(TABLE_NAME)
            .filterExpression("updatedAt < :since AND #estado = :pendiente")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(Map.of(
                ":since", AttributeValue.builder().s(since.toString()).build(),
                ":pendiente", AttributeValue.builder().s("PENDIENTE").build()
            ))
            .build();

        List<DeliveryState> results = new ArrayList<>();
        SdkIterable<ScanResponse> pages = dynamoDbClient.scanPaginator(request);
        for (ScanResponse page : pages) {
            for (Map<String, AttributeValue> item : page.items()) {
                results.add(fromItem(item));
            }
        }
        return results;
    }

    @Override
    public List<Long> findPagosSinEstadoParaDestino(String destino, Instant desde) {
        ScanRequest request = ScanRequest.builder()
            .tableName(TABLE_NAME)
            .filterExpression("destino = :dest AND #estado = :pendiente AND createdAt >= :desde")
            .expressionAttributeNames(Map.of("#estado", "estado"))
            .expressionAttributeValues(Map.of(
                ":dest", AttributeValue.builder().s(destino).build(),
                ":pendiente", AttributeValue.builder().s("PENDIENTE").build(),
                ":desde", AttributeValue.builder().s(desde.toString()).build()
            ))
            .build();

        List<Long> results = new ArrayList<>();
        SdkIterable<ScanResponse> pages = dynamoDbClient.scanPaginator(request);
        for (ScanResponse page : pages) {
            for (Map<String, AttributeValue> item : page.items()) {
                String paymentIdStr = item.get("paymentId").n();
                results.add(Long.parseLong(paymentIdStr));
            }
        }
        return results.stream().distinct().toList();
    }

    private Map<String, AttributeValue> toItem(DeliveryState state) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("paymentId", AttributeValue.builder().n(state.getPaymentId().toString()).build());
        item.put("destino", AttributeValue.builder().s(state.getDestino()).build());
        item.put("estado", AttributeValue.builder().s(state.getEstado()).build());
        item.put("intentos", AttributeValue.builder().n(String.valueOf(state.getIntentos())).build());
        item.put("idempotencyKey", AttributeValue.builder().s(state.getIdempotencyKey()).build());
        item.put("receiptId", AttributeValue.builder().s(state.getReceiptId() != null ? state.getReceiptId() : "").build());
        item.put("lastHttpStatus", AttributeValue.builder().n(state.getLastHttpStatus() != null ? String.valueOf(state.getLastHttpStatus()) : "0").build());
        item.put("lastErrorCode", AttributeValue.builder().s(state.getLastErrorCode() != null ? state.getLastErrorCode() : "").build());
        item.put("createdAt", AttributeValue.builder().s(state.getCreatedAt().toString()).build());
        item.put("updatedAt", AttributeValue.builder().s(state.getUpdatedAt().toString()).build());
        item.put("version", AttributeValue.builder().n(String.valueOf(state.getVersion())).build());
        item.put("openKey", AttributeValue.builder().s(state.getOpenKey() != null ? state.getOpenKey() : "").build());
        item.put("nextAttemptAt", AttributeValue.builder().s(state.getNextAttemptAt() != null ? state.getNextAttemptAt().toString() : "").build());
        item.put("leaseUntil", AttributeValue.builder().s(state.getLeaseUntil() != null ? state.getLeaseUntil().toString() : "").build());
        return item;
    }

    private DeliveryState fromItem(Map<String, AttributeValue> item) {
        DeliveryState state = new DeliveryState();
        state.setPaymentId(Long.parseLong(item.get("paymentId").n()));
        state.setDestino(item.get("destino").s());
        state.setEstado(item.get("estado").s());
        state.setIntentos(Integer.parseInt(item.get("intentos").n()));
        state.setIdempotencyKey(item.get("idempotencyKey").s());
        state.setReceiptId(item.get("receiptId").s().isEmpty() ? null : item.get("receiptId").s());
        state.setLastHttpStatus(item.get("lastHttpStatus").n().isEmpty() ? 0 : Integer.parseInt(item.get("lastHttpStatus").n()));
        state.setLastErrorCode(item.get("lastErrorCode").s().isEmpty() ? null : item.get("lastErrorCode").s());
        state.setCreatedAt(Instant.parse(item.get("createdAt").s()));
        state.setUpdatedAt(Instant.parse(item.get("updatedAt").s()));
        state.setVersion(Long.parseLong(item.get("version").n()));
        state.setOpenKey(item.get("openKey").s().isEmpty() ? null : item.get("openKey").s());
        state.setNextAttemptAt(item.get("nextAttemptAt").s().isEmpty() ? null : Instant.parse(item.get("nextAttemptAt").s()));
        state.setLeaseUntil(item.get("leaseUntil").s().isEmpty() ? null : Instant.parse(item.get("leaseUntil").s()));
        return state;
    }

    // Helper to build expression attribute values map with more than 10 entries
    private Map<String, AttributeValue> vals(Object... keyValuePairs) {
        Map<String, AttributeValue> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = (String) keyValuePairs[i];
            String value = (String) keyValuePairs[i + 1];
            if (value.startsWith("n:")) {
                map.put(key, AttributeValue.builder().n(value.substring(2)).build());
            } else {
                map.put(key, AttributeValue.builder().s(value).build());
            }
        }
        return map;
    }
}