package gt.edu.umg.desarrolloweb.pagos.domain;

import java.util.Locale;

/** Sistemas externos a los que se entrega cada pago. */
public enum Destino {
    BANCO,
    ERP,
    TERCERO;

    /** Sufijo en minúsculas usado en colas y claves de enrutamiento: {@code pagos.banco}. */
    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }
}
