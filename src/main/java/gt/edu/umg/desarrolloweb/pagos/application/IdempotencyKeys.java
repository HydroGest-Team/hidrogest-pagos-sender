package gt.edu.umg.desarrolloweb.pagos.application;

import org.springframework.stereotype.Component;

/**
 * Llave estable por pago: {@code <prefijo><id con 6 dígitos>}, por ejemplo {@code EQA-000123}.
 * Es la misma en todos los reintentos y en los 3 destinos; nunca un UUID por intento.
 */
@Component
public class IdempotencyKeys {

    private final String prefijo;

    public IdempotencyKeys(PagosProperties properties) {
        this.prefijo = properties.prefijoLlave().isBlank()
                ? derivarPrefijo(properties.teamCode())
                : properties.prefijoLlave();
    }

    public String forPayment(long paymentId) {
        return prefijo + String.format("%06d", paymentId);
    }

    /** {@code EQUIPO_A → EQA-}. */
    static String derivarPrefijo(String teamCode) {
        return "EQ" + teamCode.charAt(teamCode.length() - 1) + "-";
    }
}
