package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.AttemptLog;
import gt.edu.umg.desarrolloweb.pagos.domain.AttemptRecord;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryMessage;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.DestinationGateway;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.EstadoEntrega;
import gt.edu.umg.desarrolloweb.pagos.domain.LookupResult;
import gt.edu.umg.desarrolloweb.pagos.domain.MetodoPago;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Máquina de estados del consumer (sección 8.5) con la matriz de escenarios de la sección 12. */
class DeliveryProcessorTest {

    private static final long PAGO = 123;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T16:00:00Z"));
    private final InMemoryStateStore store = new InMemoryStateStore();
    private final List<AttemptRecord> bitacora = new ArrayList<>();
    private final ScriptedGateway banco = new ScriptedGateway(Destino.BANCO);
    private final ScriptedGateway erp = new ScriptedGateway(Destino.ERP);
    private final ScriptedGateway tercero = new ScriptedGateway(Destino.TERCERO);
    private DeliveryProcessor processor;

    @BeforeEach
    void setUp() {
        PagosProperties props = TestProperties.pagos();
        PaymentSource source = new PaymentSource() {
            @Override
            public List<Payment> findPagadosDesde(LocalDateTime desde, PageKey despues, int limite) {
                return List.of();
            }

            @Override
            public Optional<Payment> findById(long id) {
                return id == PAGO ? Optional.of(new Payment(PAGO, new BigDecimal("150.75"),
                        LocalDateTime.of(2026, 10, 1, 10, 15), MetodoPago.EFECTIVO, "PAGADO", "REC-1", 1, "Ana",
                        "MTR-1", YearMonth.of(2026, 9), LocalDateTime.of(2026, 10, 1, 16, 15))) : Optional.empty();
            }
        };
        AttemptLog log = new AttemptLog() {
            @Override
            public void record(AttemptRecord attempt) {
                bitacora.add(attempt);
            }

            @Override
            public List<AttemptRecord> findByPaymentAndDestino(long paymentId, Destino destino) {
                return bitacora;
            }
        };
        DestinationRegistry registry = new DestinationRegistry(List.of(banco, erp, tercero), props);
        processor = new DeliveryProcessor(store, source, log, registry,
                new PaymentValidator(clock, ZoneId.of("America/Guatemala")), new RetryPolicy(props),
                new DeliveryMetrics(new SimpleMeterRegistry()), props, clock);
        for (Destino d : Destino.values()) {
            store.createPending(PAGO, d, "EQA-000123", clock.instant());
        }
    }

    private DeliveryDecision procesar(Destino destino) {
        return processor.process(new DeliveryMessage(PAGO, destino, 0, "corr-1"));
    }

    @Test
    void exitoGuardaElComprobante() {
        banco.sends.add(DeliveryResult.delivered(201, null, "BNK-20261023-AAAAAA"));

        assertThat(procesar(Destino.BANCO)).isInstanceOf(DeliveryDecision.Ack.class);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(store.get(PAGO, Destino.BANCO).receiptId()).isEqualTo("BNK-20261023-AAAAAA");
        assertThat(store.get(PAGO, Destino.BANCO).intentos()).isEqualTo(1);
        assertThat(banco.sendKeys).containsExactly("EQA-000123");
    }

    @Test
    void duplicadoSeTrataComoEntregadoConElComprobanteOriginal() {
        banco.sends.add(DeliveryResult.delivered(409, "DUPLICATE_PAYMENT", "BNK-ORIGINAL"));

        procesar(Destino.BANCO);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(store.get(PAGO, Destino.BANCO).receiptId()).isEqualTo("BNK-ORIGINAL");
    }

    @Test
    void consultaQueEncuentraLaLlaveConOtroMontoVaADeadLetterSinReenviar() {
        banco.sends.add(new DeliveryResult(Outcome.UNKNOWN, null, "TIMEOUT", null, null, null));
        banco.lookups.add(new LookupResult.Failed(new DeliveryResult(Outcome.DEAD_LETTER, 200,
                DeliveryResult.LLAVE_REUTILIZADA, null, null, "otro monto")));

        procesar(Destino.BANCO);
        clock.advance(Duration.ofSeconds(5));
        DeliveryDecision decision = procesar(Destino.BANCO);

        assertThat(decision).isInstanceOf(DeliveryDecision.DeadLetter.class);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.DEAD_LETTER);
        assertThat(banco.sendKeys).hasSize(1);
    }

    @Test
    void error503RespetaRetryAfterYNoTocaLosOtrosDestinos() {
        erp.sends.add(new DeliveryResult(Outcome.RETRY, 503, "SERVICIO_NO_DISPONIBLE", null, Duration.ofSeconds(20), null));

        DeliveryDecision decision = procesar(Destino.ERP);
        assertThat(decision).isInstanceOf(DeliveryDecision.Retry.class);
        assertThat(((DeliveryDecision.Retry) decision).delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(store.get(PAGO, Destino.ERP).estado()).isEqualTo(EstadoEntrega.REINTENTO);
        assertThat(store.get(PAGO, Destino.ERP).intentos()).isEqualTo(1);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.PENDIENTE);
        assertThat(banco.sendKeys).isEmpty();
    }

    @Test
    void lostAckTimeoutLuegoConsultaYSeEncuentraSinReenviar() {
        banco.sends.add(new DeliveryResult(Outcome.UNKNOWN, null, "TIMEOUT", null, null, "timeout de lectura"));
        banco.lookups.add(new LookupResult.Found("BNK-REGISTRADO"));

        procesar(Destino.BANCO);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.DESCONOCIDO);

        clock.advance(Duration.ofSeconds(5));
        procesar(Destino.BANCO);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(store.get(PAGO, Destino.BANCO).receiptId()).isEqualTo("BNK-REGISTRADO");
        assertThat(banco.sendKeys).hasSize(1);
    }

    @Test
    void timeoutSinRegistroConsulta404YReenviaConLaMismaLlave() {
        banco.sends.add(new DeliveryResult(Outcome.UNKNOWN, null, "TIMEOUT", null, null, null));
        banco.lookups.add(new LookupResult.NotFound());
        banco.sends.add(DeliveryResult.delivered(201, null, "BNK-SEGUNDO"));

        procesar(Destino.BANCO);
        clock.advance(Duration.ofSeconds(5));
        procesar(Destino.BANCO);

        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(banco.sendKeys).containsExactly("EQA-000123", "EQA-000123");
    }

    @Test
    void rechazoDeNegocioVaADeadLetterSinReintentos() {
        banco.sends.add(new DeliveryResult(Outcome.DEAD_LETTER, 422, "AMOUNT_LIMIT_EXCEEDED", null, null, null));

        DeliveryDecision decision = procesar(Destino.BANCO);
        assertThat(decision).isInstanceOf(DeliveryDecision.DeadLetter.class);
        assertThat(((DeliveryDecision.DeadLetter) decision).reason()).startsWith("AMOUNT_LIMIT_EXCEEDED");
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.DEAD_LETTER);
    }

    @Test
    void agotarIntentosTerminaEnDeadLetter() {
        for (int i = 0; i < 6; i++) {
            erp.sends.add(new DeliveryResult(Outcome.RETRY, 500, "ERROR_INTERNO", null, null, null));
        }
        DeliveryDecision ultima = null;
        for (int i = 0; i < 6; i++) {
            ultima = procesar(Destino.ERP);
            clock.advance(Duration.ofMinutes(11));
        }
        assertThat(ultima).isInstanceOf(DeliveryDecision.DeadLetter.class);
        assertThat(store.get(PAGO, Destino.ERP).estado()).isEqualTo(EstadoEntrega.DEAD_LETTER);
        assertThat(store.get(PAGO, Destino.ERP).intentos()).isEqualTo(6);
    }

    @Test
    void circuitoAbiertoNoQuemaIntentos() {
        tercero.sends.add(DeliveryResult.circuitOpen());

        DeliveryDecision decision = procesar(Destino.TERCERO);
        assertThat(decision).isInstanceOf(DeliveryDecision.Retry.class);
        assertThat(store.get(PAGO, Destino.TERCERO).intentos()).isZero();
        assertThat(bitacora).isEmpty();
    }

    @Test
    void mensajeDuplicadoDeUnaEntregaTerminadaNoLlamaAlDestino() {
        banco.sends.add(DeliveryResult.delivered(201, null, "BNK-1"));
        procesar(Destino.BANCO);

        assertThat(procesar(Destino.BANCO)).isInstanceOf(DeliveryDecision.Ack.class);
        assertThat(banco.sendKeys).hasSize(1);
    }

    @Test
    void mensajeAdelantadoSeDescartaHastaSuHora() {
        erp.sends.add(new DeliveryResult(Outcome.RETRY, 500, "ERROR_INTERNO", null, null, null));
        procesar(Destino.ERP);

        // Llega otro mensaje del mismo (pago, destino) antes del próximo intento programado.
        assertThat(procesar(Destino.ERP)).isInstanceOf(DeliveryDecision.Ack.class);
        assertThat(erp.sendKeys).hasSize(1);
    }

    @Test
    void falloParcialSoloSeReenviaAlDestinoQueFalta() {
        banco.sends.add(DeliveryResult.delivered(201, null, "BNK-1"));
        erp.sends.add(new DeliveryResult(Outcome.RETRY, 503, "SERVICIO_NO_DISPONIBLE", null, null, null));
        tercero.sends.add(DeliveryResult.delivered(201, null, "THR-1"));
        procesar(Destino.BANCO);
        procesar(Destino.ERP);
        procesar(Destino.TERCERO);

        // El reconciliador encuentra solo la entrega abierta.
        clock.advance(Duration.ofMinutes(2));
        List<Destino> pendientes = store.findOverdue(clock.instant(), 100).stream().map(s -> s.destino()).toList();
        assertThat(pendientes).containsExactly(Destino.ERP);

        erp.sends.add(DeliveryResult.delivered(201, null, "ERP-1"));
        procesar(Destino.ERP);
        assertThat(store.findByPayment(PAGO)).allMatch(s -> s.estado() == EstadoEntrega.ENTREGADO);
        assertThat(banco.sendKeys).hasSize(1);
        assertThat(tercero.sendKeys).hasSize(1);
        assertThat(erp.sendKeys).hasSize(2);
    }

    @Test
    void consumerQueMurioEnVueloSeRetomaAlVencerElLeaseYConsultaPrimero() {
        // Otro consumer reclamó y murió sin cerrar el estado.
        store.claim(PAGO, Destino.BANCO, clock.instant(), clock.instant().plusSeconds(30));
        assertThat(procesar(Destino.BANCO)).isInstanceOf(DeliveryDecision.Ack.class);
        assertThat(banco.lookupCount).isZero();

        clock.advance(Duration.ofSeconds(31));
        banco.lookups.add(new LookupResult.Found("BNK-YA-ESTABA"));
        procesar(Destino.BANCO);
        assertThat(store.get(PAGO, Destino.BANCO).estado()).isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(banco.sendKeys).isEmpty();
    }

    /** Gateway que devuelve respuestas preprogramadas y registra lo que se le pidió. */
    static final class ScriptedGateway implements DestinationGateway {
        private final Destino destino;
        final Deque<DeliveryResult> sends = new ArrayDeque<>();
        final Deque<LookupResult> lookups = new ArrayDeque<>();
        final List<String> sendKeys = new ArrayList<>();
        int lookupCount;

        ScriptedGateway(Destino destino) {
            this.destino = destino;
        }

        @Override
        public Destino destino() {
            return destino;
        }

        @Override
        public DeliveryResult send(Payment payment, String key) {
            sendKeys.add(key);
            return sends.removeFirst();
        }

        @Override
        public LookupResult lookup(Payment payment, String key) {
            lookupCount++;
            return lookups.removeFirst();
        }

        @Override
        public boolean supportsLookup() {
            return true;
        }

        @Override
        public boolean healthy() {
            return true;
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
