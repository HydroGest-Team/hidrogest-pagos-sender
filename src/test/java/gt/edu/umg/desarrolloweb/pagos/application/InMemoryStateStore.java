package gt.edu.umg.desarrolloweb.pagos.application;

import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryResult;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryState;
import gt.edu.umg.desarrolloweb.pagos.domain.DeliveryStateStore;
import gt.edu.umg.desarrolloweb.pagos.domain.Destino;
import gt.edu.umg.desarrolloweb.pagos.domain.EstadoEntrega;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Misma semántica que el adaptador de DynamoDB (escrituras condicionales por versión), en memoria. */
class InMemoryStateStore implements DeliveryStateStore {

    final Map<Key, DeliveryState> rows = new ConcurrentHashMap<>();

    @Override
    public boolean createPending(long paymentId, Destino destino, String key, Instant now) {
        return rows.putIfAbsent(new Key(paymentId, destino), new DeliveryState(paymentId, destino,
                EstadoEntrega.PENDIENTE, 0, now, null, key, null, null, null, null, now, now, 0)) == null;
    }

    @Override
    public Set<Key> existing(Collection<Long> ids, Collection<Destino> destinos) {
        return rows.keySet().stream().filter(k -> ids.contains(k.paymentId()) && destinos.contains(k.destino()))
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<DeliveryState> find(long paymentId, Destino destino) {
        return Optional.ofNullable(rows.get(new Key(paymentId, destino)));
    }

    @Override
    public List<DeliveryState> findByPayment(long paymentId) {
        return rows.values().stream().filter(s -> s.paymentId() == paymentId).toList();
    }

    @Override
    public synchronized Optional<DeliveryState> claim(long paymentId, Destino destino, Instant now, Instant lease) {
        DeliveryState s = rows.get(new Key(paymentId, destino));
        boolean libre = s != null && (s.estado() == EstadoEntrega.PENDIENTE || s.estado() == EstadoEntrega.REINTENTO
                || s.estado() == EstadoEntrega.DESCONOCIDO
                || (s.estado() == EstadoEntrega.EN_VUELO && s.leaseUntil().isBefore(now)));
        if (!libre) {
            return Optional.empty();
        }
        put(s, EstadoEntrega.EN_VUELO, s.intentos(), lease, lease, s.receiptId(), null, now);
        return Optional.of(s);
    }

    @Override
    public synchronized boolean markDelivered(long id, Destino d, long v, int intentos, DeliveryResult r, Instant now) {
        return cas(id, d, v, s -> put(s, EstadoEntrega.ENTREGADO, intentos, null, null, r.receiptId(), r, now));
    }

    @Override
    public synchronized boolean markRetry(long id, Destino d, long v, EstadoEntrega e, int intentos, Instant next,
                                          DeliveryResult r, Instant now) {
        return cas(id, d, v, s -> put(s, e, intentos, next, null, s.receiptId(), r, now));
    }

    @Override
    public synchronized boolean markDeadLetter(long id, Destino d, long v, int intentos, DeliveryResult r, Instant now) {
        return cas(id, d, v, s -> put(s, EstadoEntrega.DEAD_LETTER, intentos, null, null, null, r, now));
    }

    @Override
    public synchronized boolean resetDeadLetter(long id, Destino d, Instant now) {
        DeliveryState s = rows.get(new Key(id, d));
        if (s == null || s.estado() != EstadoEntrega.DEAD_LETTER) {
            return false;
        }
        put(s, EstadoEntrega.PENDIENTE, 0, now, null, null, null, now);
        return true;
    }

    @Override
    public List<DeliveryState> findOverdue(Instant before, int limit) {
        return rows.values().stream().filter(s -> !s.esTerminal() && !s.nextAttemptAt().isAfter(before)).limit(limit).toList();
    }

    @Override
    public Map<Destino, Long> countOpen() {
        Map<Destino, Long> m = new EnumMap<>(Destino.class);
        rows.values().stream().filter(s -> !s.esTerminal()).forEach(s -> m.merge(s.destino(), 1L, Long::sum));
        return m;
    }

    DeliveryState get(long id, Destino d) {
        return rows.get(new Key(id, d));
    }

    private boolean cas(long id, Destino d, long expected, java.util.function.Consumer<DeliveryState> action) {
        DeliveryState s = rows.get(new Key(id, d));
        if (s == null || s.version() != expected) {
            return false;
        }
        action.accept(s);
        return true;
    }

    private void put(DeliveryState s, EstadoEntrega estado, int intentos, Instant next, Instant lease, String receipt,
                     DeliveryResult r, Instant now) {
        rows.put(new Key(s.paymentId(), s.destino()), new DeliveryState(s.paymentId(), s.destino(), estado, intentos,
                next, lease, s.idempotencyKey(), receipt,
                r == null ? s.lastHttpStatus() : r.httpStatus(), r == null ? s.lastErrorCode() : r.errorCode(),
                r == null ? s.lastErrorDetail() : r.detail(), s.createdAt(), now, s.version() + 1));
    }
}
