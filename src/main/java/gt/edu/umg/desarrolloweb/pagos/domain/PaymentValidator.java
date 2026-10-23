package gt.edu.umg.desarrolloweb.pagos.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validaciones previas al envío, para no gastar una llamada de red en algo que el destino
 * rechazaría con 400/422. Las reglas salen de los contratos YAML.
 */
public final class PaymentValidator {

    private static final Pattern LLAVE = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final long MAX_CENTAVOS_CONTRATO = 99_999_999L;
    private static final long MAX_CENTAVOS_BANCO = 2_500_000L;
    private static final BigDecimal MAX_MONTO_TERCERO = new BigDecimal("25000.00");
    private static final int MAX_LLAVE_ERP = 40;
    private static final int MAX_REFERENCIA_TERCERO = 30;
    private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(5);

    private final Clock clock;
    private final ZoneId zonaFechaPago;

    public PaymentValidator(Clock clock, ZoneId zonaFechaPago) {
        this.clock = clock;
        this.zonaFechaPago = zonaFechaPago;
    }

    /** Devuelve el motivo del rechazo, o vacío si el pago se puede enviar a ese destino. */
    public Optional<String> validate(Payment payment, Destino destino, String idempotencyKey) {
        if (!payment.esElegible()) {
            return Optional.of("el pago no está PAGADO");
        }
        if (idempotencyKey == null || !LLAVE.matcher(idempotencyKey).matches()) {
            return Optional.of("Idempotency-Key con formato inválido");
        }
        if (payment.monto().signum() <= 0) {
            return Optional.of("el monto debe ser mayor que cero");
        }
        long centavos;
        try {
            centavos = payment.montoEnCentavos();
        } catch (ArithmeticException e) {
            return Optional.of("el monto tiene más de 2 decimales");
        }
        if (centavos > MAX_CENTAVOS_CONTRATO) {
            return Optional.of("el monto excede el máximo del contrato");
        }
        OffsetDateTime limite = OffsetDateTime.now(clock).plus(TOLERANCIA_FUTURO);
        if (payment.fechaPagoEn(zonaFechaPago).isAfter(limite)) {
            return Optional.of("fecha_pago más de 5 minutos en el futuro");
        }
        return switch (destino) {
            case BANCO -> centavos > MAX_CENTAVOS_BANCO
                    ? Optional.of("el monto excede el límite del banco (Q25,000.00)")
                    : Optional.empty();
            case ERP -> idempotencyKey.length() > MAX_LLAVE_ERP
                    ? Optional.of("documento_origen.numero excede 40 caracteres")
                    : Optional.empty();
            case TERCERO -> validarTercero(payment);
        };
    }

    private Optional<String> validarTercero(Payment payment) {
        if (payment.monto().compareTo(MAX_MONTO_TERCERO) > 0) {
            return Optional.of("el monto excede el límite del tercero (25000.00)");
        }
        if (payment.numeroRecibo() == null || payment.numeroRecibo().isBlank()
                || payment.numeroRecibo().length() > MAX_REFERENCIA_TERCERO) {
            return Optional.of("numero_recibo vacío o mayor a 30 caracteres");
        }
        return Optional.empty();
    }
}
