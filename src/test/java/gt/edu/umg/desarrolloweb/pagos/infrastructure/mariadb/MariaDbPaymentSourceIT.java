package gt.edu.umg.desarrolloweb.pagos.infrastructure.mariadb;

import gt.edu.umg.desarrolloweb.pagos.domain.MetodoPago;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.domain.PaymentSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Consultas reales contra MariaDB con el esquema copiado de las migraciones de HidroGest. */
@Testcontainers
class MariaDbPaymentSourceIT {

    @Container
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("hidrogest")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mariadb/01-esquema-hidrogest.sql"),
                    "/docker-entrypoint-initdb.d/01-esquema-hidrogest.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/mariadb/02-datos-semilla.sql"),
                    "/docker-entrypoint-initdb.d/02-datos-semilla.sql");

    private static MariaDbPaymentSource source;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        source = new MariaDbPaymentSource(new NamedParameterJdbcTemplate(ds));
    }

    @Test
    void soloPagadosEnOrdenDeMarcaDeTiempo() {
        List<Payment> pagos = source.findPagadosDesde(LocalDateTime.of(2000, 1, 1, 0, 0), null, 100);
        assertThat(pagos).extracting(Payment::id).containsExactly(900001L, 900002L, 900003L, 900004L, 900005L);
    }

    @Test
    void llegaAlClientePorElContadorYArmaElPagoCompleto() {
        Payment p = source.findById(900002).orElseThrow();
        assertThat(p.monto()).isEqualByComparingTo(new BigDecimal("82.50"));
        assertThat(p.fechaPago()).isEqualTo(LocalDateTime.of(2026, 10, 1, 11, 30));
        assertThat(p.metodo()).isEqualTo(MetodoPago.CREDITO);
        assertThat(p.numeroRecibo()).isEqualTo("REC-2026-09-0002");
        // tb_lecturas.usuario_id es la cajera (2); el cliente sale de tb_contadores.cliente_id.
        assertThat(p.clienteId()).isEqualTo(2);
        assertThat(p.nombreCliente()).isEqualTo("Carlos Méndez Ruiz");
        assertThat(p.codigoContador()).isEqualTo("MTR-00002");
        assertThat(p.periodo()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(p.marcaTiempo()).isEqualTo(LocalDateTime.of(2026, 10, 1, 17, 30));
    }

    @Test
    void paginacionPorLlaveNoPierdeNiRepite() {
        LocalDateTime desde = LocalDateTime.of(2000, 1, 1, 0, 0);
        List<Payment> primera = source.findPagadosDesde(desde, null, 2);
        Payment ultima = primera.get(1);
        List<Payment> segunda = source.findPagadosDesde(desde, new PaymentSource.PageKey(ultima.marcaTiempo(), ultima.id()), 2);
        Payment ultima2 = segunda.get(1);
        List<Payment> tercera = source.findPagadosDesde(desde, new PaymentSource.PageKey(ultima2.marcaTiempo(), ultima2.id()), 2);

        assertThat(primera).extracting(Payment::id).containsExactly(900001L, 900002L);
        assertThat(segunda).extracting(Payment::id).containsExactly(900003L, 900004L);
        assertThat(tercera).extracting(Payment::id).containsExactly(900005L);
    }

    @Test
    void filtraPorMarcaDeTiempo() {
        assertThat(source.findPagadosDesde(LocalDateTime.of(2026, 10, 21, 0, 0), null, 100))
                .extracting(Payment::id).containsExactly(900005L);
    }

    @Test
    void pagoNoPagadoSeLeePeroNoEsElegible() {
        assertThat(source.findById(900006)).get().extracting(Payment::esElegible).isEqualTo(false);
        assertThat(source.findById(1)).isEmpty();
    }
}
