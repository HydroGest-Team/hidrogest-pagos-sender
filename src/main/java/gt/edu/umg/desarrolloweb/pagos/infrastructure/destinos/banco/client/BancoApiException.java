package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.client;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankFieldErrorDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.dto.BankPaymentReceiptDto;

import java.util.List;

public class BancoApiException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final boolean retryable;
    private final String correlationId;
    private final String timestamp;
    private final List<BankFieldErrorDto> details;
    private final BankPaymentReceiptDto originalReceipt;

    public BancoApiException(int httpStatus, String errorCode, String message, boolean retryable,
                             String correlationId, String timestamp,
                             List<BankFieldErrorDto> details, BankPaymentReceiptDto originalReceipt) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.details = details;
        this.originalReceipt = originalReceipt;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public boolean isRetryable() { return retryable; }
    public String getCorrelationId() { return correlationId; }
    public String getTimestamp() { return timestamp; }
    public List<BankFieldErrorDto> getDetails() { return details; }
    public BankPaymentReceiptDto getOriginalReceipt() { return originalReceipt; }

    public boolean isDuplicatePayment() {
        return "DUPLICATE_PAYMENT".equals(errorCode);
    }

    public boolean isIdempotencyKeyReused() {
        return "IDEMPOTENCY_KEY_REUSED".equals(errorCode);
    }
}