package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;

/**
 * Decodificador tolerante: lee el código de error y el comprobante original con rutas JSON del
 * contrato de cada destino. Si el cuerpo no es del contrato (por ejemplo {@code {"message":"Forbidden"}}
 * de API Gateway) devuelve el estado HTTP sin código; la clasificación se basa en el estado.
 */
public class ContractErrorDecoder implements ErrorDecoder {

    private static final int MAX_BODY = 64 * 1024;

    private final ObjectMapper mapper;
    private final String codeField;
    private final String originalReceiptPointer;
    private final String originalAmountPointer;
    private final boolean amountInCents;

    /**
     * @param codeField              campo con el código: {@code code} o {@code codigo_error}
     * @param originalReceiptPointer JSON pointer al comprobante en un duplicado, p. ej. {@code /originalReceipt/receiptId}
     * @param originalAmountPointer  JSON pointer al monto registrado en un duplicado
     * @param amountInCents          {@code true} si ese monto viene en centavos (entero); si no, texto en quetzales
     */
    public ContractErrorDecoder(ObjectMapper mapper, String codeField, String originalReceiptPointer,
                                String originalAmountPointer, boolean amountInCents) {
        this.mapper = mapper;
        this.codeField = codeField;
        this.originalReceiptPointer = originalReceiptPointer;
        this.originalAmountPointer = originalAmountPointer;
        this.amountInCents = amountInCents;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        JsonNode body = readBody(response);
        String code = text(body.path(codeField));
        String receipt = text(body.at(originalReceiptPointer));
        JsonNode monto = body.at(originalAmountPointer);
        BigDecimal amount = amountInCents
                ? Montos.desdeCentavos(monto.isIntegralNumber() ? monto.longValue() : null)
                : Montos.desdeTexto(text(monto));
        return new DestinationHttpException(response.status(), code, receipt, amount, retryAfter(response));
    }

    private JsonNode readBody(Response response) {
        if (response.body() == null) {
            return mapper.missingNode();
        }
        try (InputStream in = response.body().asInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BODY);
            return bytes.length == 0 ? mapper.missingNode() : mapper.readTree(bytes);
        } catch (IOException | RuntimeException e) {
            return mapper.missingNode();
        }
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    /** {@code Retry-After} en segundos (la forma de fecha HTTP no la usan estos destinos). */
    static Duration retryAfter(Response response) {
        Collection<String> values = response.headers().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("Retry-After"))
                .map(java.util.Map.Entry::getValue)
                .findFirst().orElse(java.util.List.of());
        for (String value : values) {
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds > 0) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException ignored) {
                // Valor no numérico: se ignora y se usa el backoff normal.
            }
        }
        return null;
    }
}
