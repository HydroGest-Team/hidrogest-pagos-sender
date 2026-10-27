package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

import java.math.BigDecimal;

/** Lectura tolerante de los montos que devuelven los destinos en sus comprobantes. */
public final class Montos {

    private Montos() {
    }

    /** 13525 → 135.25; {@code null} si no vino. */
    public static BigDecimal desdeCentavos(Long centavos) {
        return centavos == null ? null : BigDecimal.valueOf(centavos, 2);
    }

    /** {@code "135.25"} → 135.25; {@code null} si no vino o no es un número. */
    public static BigDecimal desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
