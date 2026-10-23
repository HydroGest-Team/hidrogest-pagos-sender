package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.LocalDateTime;
import java.util.Optional;

/** Cursores de control (tabla {@code pagos_control}), por ejemplo el del producer. */
public interface CursorStore {

    Optional<LocalDateTime> get(String nombre);

    void put(String nombre, LocalDateTime valor);
}
