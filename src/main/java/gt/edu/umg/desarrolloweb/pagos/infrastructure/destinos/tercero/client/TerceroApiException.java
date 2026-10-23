package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.client;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.dto.ThirdReceiptDto;

public class TerceroApiException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final boolean retryable;
    private final String correlationId;
    private final String timestamp;
    private final ThirdReceiptDto originalConfirmation;

    public TerceroApiException(int httpStatus, String errorCode, String message, boolean retryable,
                               String correlationId, String timestamp,
                               ThirdReceiptDto originalConfirmation) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.originalConfirmation = originalConfirmation;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public boolean isRetryable() { return retryable; }
    public String getCorrelationId() { return correlationId; }
    public String getTimestamp() { return timestamp; }
    public ThirdReceiptDto getOriginalConfirmation() { return originalConfirmation; }

    public boolean isDuplicatePayment() {
        return "DUPLICATE_PAYMENT".equals(errorCode);
    }

    public boolean isIdempotencyKeyReused() {
        return "IDEMPOTENCY_KEY_REUSED".equals(errorCode);
    }
}