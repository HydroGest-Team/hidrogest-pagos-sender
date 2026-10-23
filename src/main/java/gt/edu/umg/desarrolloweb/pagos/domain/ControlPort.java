package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import java.util.Optional;

public interface ControlPort {
    Optional<Instant> getCursor();
    void updateCursor(Instant cursor);
}