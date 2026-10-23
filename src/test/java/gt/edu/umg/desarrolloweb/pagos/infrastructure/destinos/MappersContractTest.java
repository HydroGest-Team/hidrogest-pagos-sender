package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gt.edu.umg.desarrolloweb.pagos.domain.MetodoPago;
import gt.edu.umg.desarrolloweb.pagos.domain.Payment;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.banco.BancoMapper;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.erp.ErpMapper;
import gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.tercero.TerceroMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el JSON que realmente sale por la red (mismo ObjectMapper que configura Spring Boot con
 * {@code application.yml}) contra los nombres, formatos y reglas de cada contrato YAML.
 */
class MappersContractTest {

    private static final ZoneId GT = ZoneId.of("America/Guatemala");
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static Payment pago(MetodoPago metodo) {
        return new Payment(123, new BigDecimal("150.75"), LocalDateTime.of(2026, 10, 1, 10, 15), metodo, "PAGADO",
                "REC-2026-09-0001", 1, "Ana María López Pérez", "MTR-00001", YearMonth.of(2026, 9),
                LocalDateTime.of(2026, 10, 1, 16, 15));
    }

    private JsonNode toJson(Object dto) throws Exception {
        return json.readTree(json.writeValueAsString(dto));
    }

    @Test
    void bancoMontoEnCentavosFechaConZonaYCanal() throws Exception {
        JsonNode body = toJson(new BancoMapper("EQUIPO_A", GT).toRequest(pago(MetodoPago.EFECTIVO)));

        assertThat(body.path("teamCode").asText()).isEqualTo("EQUIPO_A");
        assertThat(body.path("payer").path("customerCode").asText()).isEqualTo("CLI-0001");
        assertThat(body.path("payer").path("fullName").asText()).isEqualTo("Ana María López Pérez");
        assertThat(body.path("payer").path("taxId").asText()).isEqualTo("CF");
        assertThat(body.path("amount").path("valueInCents").isIntegralNumber()).isTrue();
        assertThat(body.path("amount").path("valueInCents").asLong()).isEqualTo(15075L);
        assertThat(body.path("amount").path("currency").asText()).isEqualTo("GTQ");
        assertThat(body.path("transactionDate").asText()).isEqualTo("2026-10-01T10:15:00-06:00");
        assertThat(body.path("channel").asText()).isEqualTo("CASH");
        assertThat(body.path("description").asText()).isEqualTo("Servicio de agua 2026-09");
        assertThat(body.path("metadata").path("sourceSystem").asText()).isEqualTo("oficina-agua-pagos");
        // La llave NO va en el body del banco (va en el header Idempotency-Key).
        assertThat(body.has("idempotencyKey")).isFalse();
        assertThat(body.toString()).doesNotContain("1000000000101");

        JsonNode tarjeta = toJson(new BancoMapper("EQUIPO_A", GT).toRequest(pago(MetodoPago.DEBITO)));
        assertThat(tarjeta.path("channel").asText()).isEqualTo("CARD");
    }

    @Test
    void erpSnakeCaseTotalComoTextoYDetalleQueCuadra() throws Exception {
        JsonNode body = toJson(new ErpMapper("EQUIPO_A", GT).toRequest(pago(MetodoPago.CREDITO), "EQA-000123"));

        assertThat(body.path("equipo").asText()).isEqualTo("EQUIPO_A");
        assertThat(body.path("documento_origen").path("tipo").asText()).isEqualTo("VOUCHER_TARJETA");
        assertThat(body.path("documento_origen").path("numero").asText()).isEqualTo("EQA-000123");
        assertThat(body.path("documento_origen").path("fecha_emision").asText()).isEqualTo("2026-10-01");
        assertThat(body.path("contribuyente").path("codigo_cliente").asText()).isEqualTo("CLI-0001");
        assertThat(body.path("contribuyente").path("codigo_contador").asText()).isEqualTo("MTR-00001");
        assertThat(body.path("contribuyente").path("nit").asText()).isEqualTo("CF");
        assertThat(body.path("periodo_fiscal").path("anio").asInt()).isEqualTo(2026);
        assertThat(body.path("periodo_fiscal").path("mes").asInt()).isEqualTo(9);
        assertThat(body.path("forma_pago").asText()).isEqualTo("TARJETA");
        assertThat(body.path("fecha_pago").asText()).isEqualTo("2026-10-01T10:15:00-06:00");
        assertThat(body.path("total").isTextual()).isTrue();
        assertThat(body.path("total").asText()).matches("^(0|[1-9][0-9]{0,8})\\.[0-9]{2}$").isEqualTo("150.75");

        JsonNode detalle = body.path("detalle");
        assertThat(detalle).hasSize(1);
        assertThat(detalle.get(0).path("rubro").asText()).isEqualTo("AGUA_POTABLE");
        assertThat(detalle.get(0).path("descripcion").asText()).isEqualTo("Consumo septiembre 2026");
        assertThat(new BigDecimal(detalle.get(0).path("monto").asText()))
                .isEqualByComparingTo(new BigDecimal(body.path("total").asText()));
        assertThat(body.path("observaciones").asText()).isEqualTo("Recibo REC-2026-09-0001");

        JsonNode efectivo = toJson(new ErpMapper("EQUIPO_A", GT).toRequest(pago(MetodoPago.EFECTIVO), "EQA-000123"));
        assertThat(efectivo.path("forma_pago").asText()).isEqualTo("EFECTIVO");
        assertThat(efectivo.path("documento_origen").path("tipo").asText()).isEqualTo("RECIBO_CAJA");
    }

    @Test
    void terceroMontoTextoPeriodoYReferencia() throws Exception {
        JsonNode body = toJson(new TerceroMapper("EQUIPO_A", GT).toRequest(pago(MetodoPago.EFECTIVO)));

        assertThat(body.path("teamCode").asText()).isEqualTo("EQUIPO_A");
        assertThat(body.path("reference").asText()).isEqualTo("REC-2026-09-0001");
        assertThat(body.path("customer").path("code").asText()).isEqualTo("CLI-0001");
        assertThat(body.path("amount").asText()).matches("^[0-9]{1,8}\\.[0-9]{2}$").isEqualTo("150.75");
        assertThat(body.path("currency").asText()).isEqualTo("GTQ");
        assertThat(body.path("paidAt").asText()).isEqualTo("2026-10-01T10:15:00-06:00");
        assertThat(body.path("method").asText()).isEqualTo("CASH");
        assertThat(body.path("period").asText()).isEqualTo("2026-09");
        assertThat(body.has("notes")).isFalse();
    }
}
