package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuerpos tomados de los ejemplos de los contratos y de las respuestas propias de API Gateway. */
class ContractErrorDecoderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ContractErrorDecoder banco = new ContractErrorDecoder(mapper, "code", "/originalReceipt/receiptId",
            "/originalReceipt/amount/valueInCents", true);
    private final ContractErrorDecoder erp = new ContractErrorDecoder(mapper, "codigo_error",
            "/comprobante_original/numero_comprobante", "/comprobante_original/total", false);

    private static Response response(int status, String body, Map<String, Collection<String>> headers) {
        Request request = Request.create(Request.HttpMethod.POST, "http://x/bank/v1/payments", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return Response.builder().status(status).request(request).headers(headers)
                .body(body, StandardCharsets.UTF_8).build();
    }

    private DestinationHttpException decode(ContractErrorDecoder decoder, Response response) {
        return (DestinationHttpException) decoder.decode("m", response);
    }

    @Test
    void duplicadoDelBancoTraeElComprobanteOriginal() {
        String body = """
                {"status":409,"code":"DUPLICATE_PAYMENT","message":"ya registrado","retryable":false,
                 "timestamp":"2026-10-07T23:15:05Z",
                 "originalReceipt":{"receiptId":"BNK-20261007-8F3K2Q","idempotencyKey":"EQA-000123",
                                   "amount":{"valueInCents":15075,"currency":"GTQ"}}}""";
        DestinationHttpException e = decode(banco, response(409, body, Map.of()));
        assertThat(e.status()).isEqualTo(409);
        assertThat(e.code()).isEqualTo("DUPLICATE_PAYMENT");
        assertThat(e.originalReceiptId()).isEqualTo("BNK-20261007-8F3K2Q");
        assertThat(e.originalAmount()).isEqualByComparingTo("150.75");
    }

    @Test
    void duplicadoDelErpEnSnakeCase() {
        String body = """
                {"estado_http":409,"codigo_error":"DOCUMENTO_DUPLICADO","mensaje":"ya contabilizado",
                 "reintentable":false,"fecha_hora":"2026-10-07T23:15:05Z",
                 "comprobante_original":{"numero_comprobante":"ERP-20261007-Q7W2ZX","total":"150.75"}}""";
        DestinationHttpException e = decode(erp, response(409, body, Map.of()));
        assertThat(e.code()).isEqualTo("DOCUMENTO_DUPLICADO");
        assertThat(e.originalReceiptId()).isEqualTo("ERP-20261007-Q7W2ZX");
        assertThat(e.originalAmount()).isEqualByComparingTo("150.75");
    }

    @Test
    void toleraCuerposDeApiGatewayFueraDeContrato() {
        DestinationHttpException forbidden = decode(banco, response(403, "{\"message\":\"Forbidden\"}", Map.of()));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.code()).isNull();

        DestinationHttpException timeout = decode(erp, response(504, "{\"message\":\"Endpoint request timed out\"}", Map.of()));
        assertThat(timeout.status()).isEqualTo(504);

        DestinationHttpException html = decode(banco, response(502, "<html>Bad Gateway</html>", Map.of()));
        assertThat(html.status()).isEqualTo(502);
        assertThat(html.code()).isNull();
    }

    @Test
    void leeRetryAfterEnSegundos() {
        DestinationHttpException e = decode(banco, response(503,
                "{\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\"}", Map.of("Retry-After", List.of("7"))));
        assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(7));
        assertThat(decode(banco, response(429, "{}", Map.of())).retryAfter()).isNull();
    }
}
