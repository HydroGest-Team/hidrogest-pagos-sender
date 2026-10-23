package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.AttemptLog;
import gt.edu.umg.desarrolloweb.pagos.domain.AttemptLogPort;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;

import java.time.Instant;

@Repository
public class DynamoDbAttemptLogAdapter implements AttemptLogPort {

    private final DynamoDbTable<AttemptLog> table;

    public DynamoDbAttemptLogAdapter(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("pagos_intentos", TableSchema.fromBean(AttemptLog.class));
    }

    @Override
    public void save(AttemptLog attempt) {
        table.putItem(PutItemEnhancedRequest.builder(AttemptLog.class)
            .item(attempt)
            .build());
    }

    public static AttemptLog createLog(Long paymentId, String destino, String estado, Integer httpStatus,
                                        String codigoError, String correlationId, String receiptId, Long duracionMs) {
        AttemptLog log = new AttemptLog();
        log.setPk(AttemptLog.pk(paymentId, destino));
        log.setSk(AttemptLog.sk(Instant.now(), 0));
        log.setEstado(estado);
        log.setHttpStatus(httpStatus);
        log.setCodigoError(codigoError);
        log.setCorrelationId(correlationId);
        log.setReceiptId(receiptId);
        log.setDuracionMs(duracionMs);
        log.setTimestamp(Instant.now());
        return log;
    }
}