package gt.edu.umg.desarrolloweb.pagos.domain;

/**
 * Puerto de salida hacia un sistema externo. Cada API (Banco, ERP, Tercero) es un adaptador;
 * agregar o quitar un destino no toca producer, consumer, estado ni reconciliador.
 * Ninguna implementación lanza excepciones: todo se traduce a {@link DeliveryResult}.
 */
public interface DestinationGateway {

    Destino destino();

    DeliveryResult send(Payment payment, String idempotencyKey);

    /**
     * Consulta si el destino ya tiene el pago con esta llave. Solo devuelve {@code Found} si lo
     * registrado coincide con {@code payment}; si la llave existe con otro monto, devuelve {@code Failed}
     * con un resultado {@code DEAD_LETTER}.
     */
    LookupResult lookup(Payment payment, String idempotencyKey);

    boolean supportsLookup();

    /** {@code true} si el endpoint de salud del destino responde. */
    boolean healthy();
}
