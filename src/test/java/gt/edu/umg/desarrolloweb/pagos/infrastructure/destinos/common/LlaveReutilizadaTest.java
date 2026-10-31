package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.LookupResult;
import gt.edu.umg.desarrolloweb.pagos.domain.MetodoPago;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un 409 duplicado o una consulta exitosa solo cuentan como entregado si el destino registró la llave
 * con el mismo monto. Caso real: la réplica local reutilizó el id 900007 y el simulador respondió
 * DUPLICATE_PAYMENT con el comprobante de un pago anterior de otro monto.
 */
class LlaveReutilizadaTest {

    private static final Payment PAGO = new Payment(900007, new BigDecimal("142.35"),
            LocalDateTime.of(2026, 10, 27, 16, 39), MetodoPago.DEBITO, "PAGADO", "REC-1", 1, "Ana",
            "MTR-1", YearMonth.of(2026, 10), LocalDateTime.of(2026, 10, 27, 16, 39));

    /** Gateway cuyo envío y consulta hacen lo que diga cada prueba. */
    private static final class StubGateway extends HttpDestinationGateway {
        Supplier<String> send;
        Supplier<Registro> lookup;

        StubGateway() {
            super(Destino.BANCO, CircuitBreakerRegistry.ofDefaults());
        }

        @Override
        protected String doSend(Payment payment, String idempotencyKey) {
            return send.get();
        }

        @Override
        protected Registro doLookup(String idempotencyKey) {
            return lookup.get();
        }

        @Override
        protected boolean doHealth() {
            return true;
        }
    }

    private static DestinationHttpException duplicado(String monto) {
        return new DestinationHttpException(409, "DUPLICATE_PAYMENT", "BNK-20261023-9RU4NH",
                monto == null ? null : new BigDecimal(monto), null);
    }

    @Test
    void duplicadoConElMismoMontoEsEntregado() {
        StubGateway gateway = new StubGateway();
        gateway.send = () -> { throw duplicado("142.350"); };

        DeliveryResult r = gateway.send(PAGO, "EQA-900007");
        assertThat(r.outcome()).isEqualTo(Outcome.DELIVERED);
        assertThat(r.receiptId()).isEqualTo("BNK-20261023-9RU4NH");
    }

    @Test
    void duplicadoConOtroMontoVaADeadLetterSinComprobante() {
        StubGateway gateway = new StubGateway();
        gateway.send = () -> { throw duplicado("135.25"); };

        DeliveryResult r = gateway.send(PAGO, "EQA-900007");
        assertThat(r.outcome()).isEqualTo(Outcome.DEAD_LETTER);
        assertThat(r.errorCode()).isEqualTo(DeliveryResult.LLAVE_REUTILIZADA);
        assertThat(r.httpStatus()).isEqualTo(409);
        assertThat(r.receiptId()).isNull();
        assertThat(r.detail()).contains("135.25");
    }

    @Test
    void duplicadoSinMontoEnLaRespuestaSeConfiaEnElDestino() {
        StubGateway gateway = new StubGateway();
        gateway.send = () -> { throw duplicado(null); };

        assertThat(gateway.send(PAGO, "EQA-900007").outcome()).isEqualTo(Outcome.DELIVERED);
    }

    @Test
    void consultaConElMismoMontoEncuentraElPago() {
        StubGateway gateway = new StubGateway();
        gateway.lookup = () -> new HttpDestinationGateway.Registro("BNK-1", new BigDecimal("142.35"));

        assertThat(gateway.lookup(PAGO, "EQA-900007")).isEqualTo(new LookupResult.Found("BNK-1"));
    }

    @Test
    void consultaConOtroMontoEsDeadLetter() {
        StubGateway gateway = new StubGateway();
        gateway.lookup = () -> new HttpDestinationGateway.Registro("BNK-1", new BigDecimal("135.25"));

        LookupResult r = gateway.lookup(PAGO, "EQA-900007");
        assertThat(r).isInstanceOf(LookupResult.Failed.class);
        DeliveryResult result = ((LookupResult.Failed) r).result();
        assertThat(result.outcome()).isEqualTo(Outcome.DEAD_LETTER);
        assertThat(result.errorCode()).isEqualTo(DeliveryResult.LLAVE_REUTILIZADA);
    }
}
