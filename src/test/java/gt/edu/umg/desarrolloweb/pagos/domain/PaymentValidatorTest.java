package gt.edu.umg.desarrolloweb.pagos.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentValidatorTest {

    private static final ZoneId GT = ZoneId.of("America/Guatemala");
    /** 2026-10-23 10:00 en Guatemala. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T16:00:00Z"), ZoneOffset.UTC);
    private final PaymentValidator validator = new PaymentValidator(clock, GT);

    static Payment pago(String monto, LocalDateTime fecha) {
        return new Payment(123, new BigDecimal(monto), fecha, MetodoPago.EFECTIVO, "PAGADO", "REC-1", 1,
                "Ana López", "MTR-1", YearMonth.of(2026, 9), fecha);
    }

    @Test
    void pagoNormalEsValidoParaLosTresDestinos() {
        Payment p = pago("150.75", LocalDateTime.of(2026, 10, 1, 10, 15));
        for (Destino d : Destino.values()) {
            assertThat(validator.validate(p, d, "EQA-000123")).isEmpty();
        }
    }

    @Test
    void conversionACentavosSinPerderPrecision() {
        assertThat(pago("150.75", LocalDateTime.now()).montoEnCentavos()).isEqualTo(15075L);
        assertThat(pago("0.10", LocalDateTime.now()).montoEnCentavos()).isEqualTo(10L);
        assertThat(pago("25000.00", LocalDateTime.now()).montoEnCentavos()).isEqualTo(2_500_000L);
        assertThat(pago("150.7", LocalDateTime.now()).montoTexto()).isEqualTo("150.70");
    }

    @Test
    void limiteDeQ25000EnBancoYTercero() {
        Payment limite = pago("25000.00", LocalDateTime.of(2026, 10, 1, 10, 0));
        Payment excede = pago("25000.01", LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(validator.validate(limite, Destino.BANCO, "EQA-000123")).isEmpty();
        assertThat(validator.validate(excede, Destino.BANCO, "EQA-000123")).isPresent();
        assertThat(validator.validate(excede, Destino.TERCERO, "EQA-000123")).isPresent();
        // El ERP no tiene ese límite en su contrato.
        assertThat(validator.validate(excede, Destino.ERP, "EQA-000123")).isEmpty();
    }

    @Test
    void fechaFuturaSeInterpretaEnHoraDeGuatemala() {
        // 10:04 en Guatemala = 4 min en el futuro: se permite (tolerancia 5 min).
        assertThat(validator.validate(pago("10.00", LocalDateTime.of(2026, 10, 23, 10, 4)), Destino.BANCO, "EQA-000123")).isEmpty();
        // 10:06 en Guatemala: rechazado.
        assertThat(validator.validate(pago("10.00", LocalDateTime.of(2026, 10, 23, 10, 6)), Destino.BANCO, "EQA-000123")).isPresent();
    }

    @Test
    void rechazaMontoCeroYPagoNoPagado() {
        assertThat(validator.validate(pago("0.00", LocalDateTime.of(2026, 10, 1, 1, 0)), Destino.BANCO, "EQA-000123")).isPresent();
        Payment noPagado = new Payment(1, BigDecimal.TEN, LocalDateTime.of(2026, 10, 1, 1, 0), MetodoPago.EFECTIVO,
                "NO PAGADO", "R", 1, "A", "M", YearMonth.of(2026, 9), null);
        assertThat(validator.validate(noPagado, Destino.BANCO, "EQA-000123")).isPresent();
    }

    @Test
    void rechazaLlaveConFormatoInvalido() {
        Payment p = pago("10.00", LocalDateTime.of(2026, 10, 1, 1, 0));
        assertThat(validator.validate(p, Destino.BANCO, "EQA-1")).isPresent();
        assertThat(validator.validate(p, Destino.BANCO, "EQA 000123")).isPresent();
    }
}
