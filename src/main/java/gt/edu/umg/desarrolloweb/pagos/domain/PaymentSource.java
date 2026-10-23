package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Lectura (solo lectura) de pagos en la BD de HidroGest. */
public interface PaymentSource {

    /**
     * Pagos {@code PAGADO} con marca de tiempo {@code >= desde}, en orden {@code (marcaTiempo, id)},
     * empezando después de {@code despues} (paginación por llave; {@code null} = desde el inicio).
     */
    List<Payment> findPagadosDesde(LocalDateTime desde, PageKey despues, int limite);

    Optional<Payment> findById(long id);

    /** Posición de paginación: última {@code (marcaTiempo, id)} leída. */
    record PageKey(LocalDateTime marcaTiempo, long id) {}
}
