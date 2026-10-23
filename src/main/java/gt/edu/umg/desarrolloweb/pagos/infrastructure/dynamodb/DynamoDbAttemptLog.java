package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.AttemptLog;
import gt.edu.umg.desarrolloweb.pagos.domain.AttemptRecord;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bitácora de intentos en {@code pagos_intentos} (un registro por llamada, exitosa o fallida). */
@Repository
public class DynamoDbAttemptLog implements AttemptLog {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbAttemptLog.class);

    private final DynamoDbClient client;
    private final String table;
    private final Duration retencion;

    public DynamoDbAttemptLog(DynamoDbClient client, DynamoDbTables tables, DynamoDbProperties properties) {
        this.client = client;
        this.table = tables.intentos();
        this.retencion = properties.retencionIntentos();
    }

    @Override
    public void record(AttemptRecord a) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attr.s(pk(a.paymentId(), a.destino())));
        item.put("sk", Attr.s(String.format("%013d#%03d#%s", a.timestamp().toEpochMilli(), a.intento(), a.operacion())));
        item.put("paymentId", Attr.n(a.paymentId()));
        item.put("destino", Attr.s(a.destino().name()));
        item.put("intento", Attr.n(a.intento()));
        item.put("timestamp", Attr.iso(a.timestamp()));
        item.put("operacion", Attr.s(a.operacion()));
        item.put("estado", Attr.s(a.outcome() == Outcome.DELIVERED ? "EXITOSO" : "FALLIDO"));
        item.put("outcome", Attr.s(a.outcome().name()));
        item.put("httpStatus", Attr.n(a.httpStatus()));
        item.put("codigoError", Attr.s(a.errorCode()));
        item.put("receiptId", Attr.s(a.receiptId()));
        item.put("correlationId", Attr.s(a.correlationId()));
        item.put("duracionMs", Attr.n(a.duracionMs()));
        item.put("expiresAt", Attr.n(a.timestamp().plus(retencion).getEpochSecond()));
        item.values().removeIf(Objects::isNull);
        try {
            client.putItem(r -> r.tableName(table).item(item));
        } catch (RuntimeException e) {
            // La bitácora no debe tumbar una entrega que ya ocurrió: el estado es lo que manda.
            log.error("No se pudo registrar el intento en la bitácora: {}", e.toString());
        }
    }

    @Override
    public List<AttemptRecord> findByPaymentAndDestino(long paymentId, Destino destino) {
        return client.queryPaginator(QueryRequest.builder()
                        .tableName(table)
                        .keyConditionExpression("pk = :pk")
                        .expressionAttributeValues(Map.of(":pk", Attr.s(pk(paymentId, destino))))
                        .build())
                .items().stream()
                .map(item -> new AttemptRecord(
                        Attr.lng(item, "paymentId"),
                        Destino.valueOf(Attr.str(item, "destino")),
                        Attr.integer(item, "intento"),
                        Attr.fromIso(item, "timestamp"),
                        Attr.str(item, "operacion"),
                        Outcome.valueOf(Attr.str(item, "outcome")),
                        Attr.integer(item, "httpStatus"),
                        Attr.str(item, "codigoError"),
                        Attr.str(item, "receiptId"),
                        Attr.str(item, "correlationId"),
                        Attr.lng(item, "duracionMs")))
                .toList();
    }

    private static String pk(long paymentId, Destino destino) {
        return paymentId + "#" + destino.name();
    }
}
