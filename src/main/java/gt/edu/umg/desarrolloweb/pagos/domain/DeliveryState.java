package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

@DynamoDbBean
public class DeliveryState {
    private Long paymentId;
    private String destino;
    private String estado;
    private Integer intentos;
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private String idempotencyKey;
    private String receiptId;
    private Integer lastHttpStatus;
    private String lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private String openKey;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("paymentId")
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("destino")
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    @DynamoDbAttribute("estado")
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    @DynamoDbAttribute("intentos")
    public Integer getIntentos() { return intentos; }
    public void setIntentos(Integer intentos) { this.intentos = intentos; }

    @DynamoDbAttribute("nextAttemptAt")
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    @DynamoDbAttribute("leaseUntil")
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }

    @DynamoDbAttribute("idempotencyKey")
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    @DynamoDbAttribute("receiptId")
    public String getReceiptId() { return receiptId; }
    public void setReceiptId(String receiptId) { this.receiptId = receiptId; }

    @DynamoDbAttribute("lastHttpStatus")
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public void setLastHttpStatus(Integer lastHttpStatus) { this.lastHttpStatus = lastHttpStatus; }

    @DynamoDbAttribute("lastErrorCode")
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @DynamoDbAttribute("version")
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @DynamoDbAttribute("openKey")
    public String getOpenKey() { return openKey; }
    public void setOpenKey(String openKey) { this.openKey = openKey; }

    public boolean isTerminal() {
        return "ENTREGADO".equals(estado) || "DEAD_LETTER".equals(estado);
    }

    public boolean isAbierto() {
        return !isTerminal();
    }
}