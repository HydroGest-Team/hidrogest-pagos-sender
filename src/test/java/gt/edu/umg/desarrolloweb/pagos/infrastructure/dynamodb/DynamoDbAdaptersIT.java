package gt.edu.umg.desarrolloweb.pagos.infrastructure.dynamodb;

import gt.edu.umg.desarrolloweb.pagos.domain.AttemptRecord;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.EstadoEntrega;
import gt.edu.umg.desarrolloweb.pagos.domain.Outcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Adaptadores contra DynamoDB Local real: escrituras condicionales, índice disperso y bitácora. */
@Testcontainers
class DynamoDbAdaptersIT {

    @Container
    static final GenericContainer<?> DYNAMO = new GenericContainer<>("amazon/dynamodb-local:2.5.2")
            .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb")
            .withExposedPorts(8000);

    private static final AtomicLong IDS = new AtomicLong(1000);
    private static DynamoDbDeliveryStateStore store;
    private static DynamoDbAttemptLog attempts;
    private static DynamoDbCursorStore cursors;

    @BeforeAll
    static void setUp() {
        DynamoDbProperties props = new DynamoDbProperties("us-east-1",
                "http://" + DYNAMO.getHost() + ":" + DYNAMO.getMappedPort(8000), true,
                "pagos_entregas", "pagos_intentos", "pagos_control", Duration.ofDays(90));
        DynamoDbClient client = new DynamoDbConfig().dynamoDbClient(props);
        DynamoDbTables tables = new DynamoDbTables(client, props);
        tables.createIfMissing();
        tables.createIfMissing(); // idempotente
        store = new DynamoDbDeliveryStateStore(client, tables);
        attempts = new DynamoDbAttemptLog(client, tables, props);
        cursors = new DynamoDbCursorStore(client, tables);
    }

    private final Instant now = Instant.parse("2026-10-23T16:00:00Z");

    @Test
    void crearPendienteEsIdempotente() {
        long id = IDS.incrementAndGet();
        assertThat(store.createPending(id, Destino.BANCO, "EQA-00" + id, now)).isTrue();
        assertThat(store.createPending(id, Destino.BANCO, "EQA-00" + id, now)).isFalse();
        assertThat(store.createPending(id, Destino.ERP, "EQA-00" + id, now)).isTrue();
        assertThat(store.existing(List.of(id, 999_999L), List.of(Destino.values())))
                .containsExactlyInAnyOrder(new DeliveryStateStore.Key(id, Destino.BANCO), new DeliveryStateStore.Key(id, Destino.ERP));
    }

    @Test
    void soloUnConsumerReclamaYElLeaseVencidoSePuedeRetomar() {
        long id = IDS.incrementAndGet();
        store.createPending(id, Destino.BANCO, "EQA-00" + id, now);

        Optional<DeliveryState> primero = store.claim(id, Destino.BANCO, now, now.plusSeconds(30));
        assertThat(primero).get().extracting(DeliveryState::estado).isEqualTo(EstadoEntrega.PENDIENTE);
        assertThat(store.claim(id, Destino.BANCO, now.plusSeconds(1), now.plusSeconds(31))).isEmpty();

        Optional<DeliveryState> retomado = store.claim(id, Destino.BANCO, now.plusSeconds(31), now.plusSeconds(61));
        assertThat(retomado).get().extracting(DeliveryState::estado).isEqualTo(EstadoEntrega.EN_VUELO);
        assertThat(retomado.get().version()).isEqualTo(primero.get().version() + 1);
    }

    @Test
    void entregadoNuncaSeSobrescribeYSaleDelIndiceDeAbiertos() {
        long id = IDS.incrementAndGet();
        store.createPending(id, Destino.ERP, "EQA-00" + id, now);
        DeliveryState previo = store.claim(id, Destino.ERP, now, now.plusSeconds(30)).orElseThrow();
        long version = previo.version() + 1;

        assertThat(store.markDelivered(id, Destino.ERP, version, 1, DeliveryResult.delivered(201, null, "ERP-20261023-XXXXXX"), now)).isTrue();
        // Una escritura con la versión vieja (otro consumer) falla.
        assertThat(store.markRetry(id, Destino.ERP, version, EstadoEntrega.REINTENTO, 2, now,
                new DeliveryResult(Outcome.RETRY, 500, "ERROR_INTERNO", null, null, null), now)).isFalse();
        assertThat(store.claim(id, Destino.ERP, now.plusSeconds(60), now.plusSeconds(90))).isEmpty();

        DeliveryState fin = store.find(id, Destino.ERP).orElseThrow();
        assertThat(fin.estado()).isEqualTo(EstadoEntrega.ENTREGADO);
        assertThat(fin.receiptId()).isEqualTo("ERP-20261023-XXXXXX");
        assertThat(fin.intentos()).isEqualTo(1);
        assertThat(store.findOverdue(now.plus(Duration.ofDays(365)), 1000))
                .noneMatch(s -> s.paymentId() == id && s.destino() == Destino.ERP);
    }

    @Test
    void reintentoQuedaEnElIndiceConSuProximaHora() {
        long id = IDS.incrementAndGet();
        store.createPending(id, Destino.TERCERO, "EQA-00" + id, now);
        long version = store.claim(id, Destino.TERCERO, now, now.plusSeconds(30)).orElseThrow().version() + 1;
        Instant proximo = now.plusSeconds(120);
        assertThat(store.markRetry(id, Destino.TERCERO, version, EstadoEntrega.REINTENTO, 1, proximo,
                new DeliveryResult(Outcome.RETRY, 503, "SERVICE_UNAVAILABLE", null, null, "detalle"), now)).isTrue();

        assertThat(store.findOverdue(now.plusSeconds(119), 1000)).noneMatch(s -> s.paymentId() == id);
        assertThat(store.findOverdue(now.plusSeconds(120), 1000)).anyMatch(s -> s.paymentId() == id
                && s.estado() == EstadoEntrega.REINTENTO && s.lastHttpStatus() == 503 && s.intentos() == 1);
        assertThat(store.countOpen().get(Destino.TERCERO)).isPositive();
    }

    @Test
    void deadLetterSoloSeReactivaManualmente() {
        long id = IDS.incrementAndGet();
        store.createPending(id, Destino.BANCO, "EQA-00" + id, now);
        long version = store.claim(id, Destino.BANCO, now, now.plusSeconds(30)).orElseThrow().version() + 1;
        assertThat(store.markDeadLetter(id, Destino.BANCO, version, 1,
                new DeliveryResult(Outcome.DEAD_LETTER, 422, "AMOUNT_LIMIT_EXCEEDED", null, null, null), now)).isTrue();
        assertThat(store.findOverdue(now.plus(Duration.ofDays(365)), 1000)).noneMatch(s -> s.paymentId() == id);

        assertThat(store.resetDeadLetter(id, Destino.BANCO, now)).isTrue();
        assertThat(store.resetDeadLetter(id, Destino.BANCO, now)).isFalse();
        assertThat(store.find(id, Destino.BANCO)).get().extracting(DeliveryState::estado).isEqualTo(EstadoEntrega.PENDIENTE);
    }

    @Test
    void bitacoraYCursor() {
        long id = IDS.incrementAndGet();
        attempts.record(new AttemptRecord(id, Destino.BANCO, 1, now, "SEND", Outcome.UNKNOWN, null, "TIMEOUT", null, "c-1", 3001));
        attempts.record(new AttemptRecord(id, Destino.BANCO, 1, now.plusSeconds(5), "LOOKUP", Outcome.DELIVERED, 200, null, "BNK-1", "c-1", 120));
        assertThat(attempts.findByPaymentAndDestino(id, Destino.BANCO))
                .extracting(AttemptRecord::operacion).containsExactly("SEND", "LOOKUP");

        assertThat(cursors.get("prueba-" + id)).isEmpty();
        cursors.put("prueba-" + id, LocalDateTime.of(2026, 10, 21, 14, 5));
        assertThat(cursors.get("prueba-" + id)).contains(LocalDateTime.of(2026, 10, 21, 14, 5));
    }
}
