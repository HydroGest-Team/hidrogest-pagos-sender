package gt.edu.umg.desarrolloweb.pagos.domain;

/** Respuesta de la consulta de reconciliación ({@code GET} por llave) a un destino. */
public sealed interface LookupResult {

    /** El destino ya tiene el pago. */
    record Found(String receiptId) implements LookupResult {}

    /** El destino respondió 404: el pago no existe, hay que enviarlo. */
    record NotFound() implements LookupResult {}

    /** No se pudo saber (timeout, 5xx, circuito abierto). */
    record Failed(DeliveryResult result) implements LookupResult {}
}
