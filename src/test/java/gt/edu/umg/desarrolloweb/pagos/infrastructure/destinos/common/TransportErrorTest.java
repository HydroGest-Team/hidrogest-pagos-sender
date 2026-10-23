package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import feign.Request;
import feign.RetryableException;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Errores de red tal como los entrega Feign ({@link RetryableException} con la causa de E/S). */
class TransportErrorTest {

    private static RetryableException feign(Throwable cause) {
        Request request = Request.create(Request.HttpMethod.POST, "http://x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.POST, cause, (Long) null, request);
    }

    @Test
    void sinConexionSeReintentaPorqueElRequestNoSalio() {
        DeliveryResult r = HttpDestinationGateway.fromTransportError(feign(new ConnectException("Connection refused")));
        assertThat(r.outcome()).isEqualTo(Outcome.RETRY);
        assertThat(r.errorCode()).isEqualTo("SIN_CONEXION");
    }

    @Test
    void timeoutDeLecturaEsDesconocido() {
        DeliveryResult r = HttpDestinationGateway.fromTransportError(feign(new SocketTimeoutException("Read timed out")));
        assertThat(r.outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(r.errorCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void timeoutDeConexionSeReintenta() {
        DeliveryResult r = HttpDestinationGateway.fromTransportError(feign(new SocketTimeoutException("Connect timed out")));
        assertThat(r.outcome()).isEqualTo(Outcome.RETRY);
    }
}
