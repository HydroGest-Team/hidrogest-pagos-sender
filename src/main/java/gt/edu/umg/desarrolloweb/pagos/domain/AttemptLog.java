package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

@DynamoDbBean
public class AttemptLog {
    private String pk;
    private String sk;
    private String estado;
    private Integer httpStatus;
    private String codigoError;
    private String correlationId;
    private String receiptId;
    private Long duracionMs;
    private Instant timestamp;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @DynamoDbAttribute("sk")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    @DynamoDbAttribute("estado")
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    @DynamoDbAttribute("httpStatus")
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    @DynamoDbAttribute("codigoError")
    public String getCodigoError() { return codigoError; }
    public void setCodigoError(String codigoError) { this.codigoError = codigoError; }

    @DynamoDbAttribute("correlationId")
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    @DynamoDbAttribute("receiptId")
    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String receiptId) { this.receiptId = receiptId; }

    @DynamoDbAttribute("duracionMs")
    public Long getDuracionMs() { return duracionMs; }
    public void setDuracionMs(Long duracionMs) { this.duracionMs = duracionMs; }

    @DynamoDbAttribute("timestamp")
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static String pk(Long paymentId, String destino) {
        return paymentId + "#" + destino;
    }

    public static String sk(Instant timestamp, int intento) {
        return timestamp.toString() + "#" + intento;
    }
}