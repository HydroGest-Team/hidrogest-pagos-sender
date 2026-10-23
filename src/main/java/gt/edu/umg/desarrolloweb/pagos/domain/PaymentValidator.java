package gt.edu.umg.desarrolloweb.pagos.domain;

import java.math.BigDecimal;

public class PaymentValidator {

    private static final long MAX_CENTAVOS_BANCO = 2_500_000L;
    private static final BigDecimal MAX_MONTO_TERCERO = new BigDecimal("25000.00");
    private static final String IDEMPOTENCY_KEY_PATTERN = "^[A-Za-z0-9_-]{8,64}$";

    public static ValidationResult validate(Payment payment, String idempotencyKey, Destino destino) {
        if (!payment.esElegible()) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Pago no está en estado PAGADO");
        }
        if (payment.monto() == null || payment.monto().signum() <= 0) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Monto debe ser mayor a cero");
        }

        long centavos = payment.montoEnCentavos();
        if (centavos < 1 || centavos > 99_999_999) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Monto fuera de rango permitido (1-99,999,999 centavos)");
        }

        if (destino == Destino.BANCO && centavos > MAX_CENTAVOS_BANCO) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Monto excede límite del banco (Q25,000)");
        }

        if (destino == Destino.TERCERO && payment.monto().compareTo(MAX_MONTO_TERCERO) > 0) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Monto excede límite del tercero (Q25,000)");
        }

        if (payment.fechaPago() != null) {
            var now = java.time.LocalDateTime.now(Payment.ZONA_HORARIA);
            if (payment.fechaPago().isAfter(now.plusMinutes(5))) {
                return ValidationResult.invalid("VALIDACION_LOCAL", "Fecha de pago más de 5 minutos en el futuro");
            }
        }

        if (!payment.metodoPago().matches("(?i)Efectivo|Credito|Debito")) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Método de pago no soportado: " + payment.metodoPago());
        }

        if (idempotencyKey == null || !idempotencyKey.matches(IDEMPOTENCY_KEY_PATTERN)) {
            return ValidationResult.invalid("VALIDACION_LOCAL", "Idempotency-Key inválida: " + idempotencyKey);
        }

        return ValidationResult.valid();
    }

    public static record ValidationResult(boolean isValid, String code, String message) {
        public static ValidationResult valid() { return new ValidationResult(true, null, null); }
        public static ValidationResult invalid(String code, String message) { return new ValidationResult(false, code, message); }
    }
}