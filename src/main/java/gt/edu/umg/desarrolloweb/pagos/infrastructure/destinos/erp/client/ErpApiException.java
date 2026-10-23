package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.client;

import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpComprobanteDto;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.dto.ErpErrorCampoDto;

import java.util.List;

public class ErpApiException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;
    private final boolean retryable;
    private final String correlationId;
    private final String timestamp;
    private final List<ErpErrorCampoDto> details;
    private final ErpComprobanteDto comprobanteOriginal;

    public ErpApiException(int httpStatus, String errorCode, String message, boolean retryable,
                           String correlationId, String timestamp,
                           List<ErpErrorCampoDto> details, ErpComprobanteDto comprobanteOriginal) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.details = details;
        this.comprobanteOriginal = comprobanteOriginal;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public boolean isRetryable() { return retryable; }
    public String getCorrelationId() { return correlationId; }
    public String getTimestamp() { return timestamp; }
    public List<ErpErrorCampoDto> getDetails() { return details; }
    public ErpComprobanteDto getComprobanteOriginal() { return comprobanteOriginal; }

    public boolean isDocumentoDuplicado() {
        return "DOCUMENTO_DUPLICADO".equals(errorCode);
    }

    public boolean isDocumentoReutilizado() {
        return "DOCUMENTO_REUTILIZADO".equals(errorCode);
    }
}