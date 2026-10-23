package gt.edu.umg.desarrolloweb.pagos.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Estado de entrega por (pago, destino). Todas las transiciones son escrituras condicionales:
 * {@code ENTREGADO} nunca se sobrescribe y solo quien reclamó un envío puede cerrarlo.
 */
public interface DeliveryStateStore {

    /** Crea el estado {@code PENDIENTE}; {@code false} si ya existía. */
    boolean createPending(long paymentId, Destino destino, String idempotencyKey, Instant now);

    /** Claves (pago, destino) que ya tienen estado, entre las pedidas. */
    Set<Key> existing(Collection<Long> paymentIds, Collection<Destino> destinos);

    Optional<DeliveryState> find(long paymentId, Destino destino);

    List<DeliveryState> findByPayment(long paymentId);

    /**
     * Reclama el envío ({@code → EN_VUELO}) si está abierto y nadie lo tiene con lease vigente.
     * Devuelve el estado <b>anterior</b> al reclamo; la nueva versión es {@code anterior.version + 1}.
     */
    Optional<DeliveryState> claim(long paymentId, Destino destino, Instant now, Instant leaseUntil);

    boolean markDelivered(long paymentId, Destino destino, long expectedVersion, int intentos,
                          DeliveryResult result, Instant now);

    /** Programa otro intento ({@code REINTENTO} o {@code DESCONOCIDO}). */
    boolean markRetry(long paymentId, Destino destino, long expectedVersion, EstadoEntrega estado,
                      int intentos, Instant nextAttemptAt, DeliveryResult result, Instant now);

    boolean markDeadLetter(long paymentId, Destino destino, long expectedVersion, int intentos,
                           DeliveryResult result, Instant now);

    /** Vuelve un {@code DEAD_LETTER} a {@code PENDIENTE} (reencolado manual tras corregir la causa). */
    boolean resetDeadLetter(long paymentId, Destino destino, Instant now);

    /** Estados abiertos cuyo próximo intento venció antes de {@code before} (mensaje perdido o atascado). */
    List<DeliveryState> findOverdue(Instant before, int limit);

    /** Cantidad de estados abiertos por destino. */
    Map<Destino, Long> countOpen();

    record Key(long paymentId, Destino destino) {}
}
