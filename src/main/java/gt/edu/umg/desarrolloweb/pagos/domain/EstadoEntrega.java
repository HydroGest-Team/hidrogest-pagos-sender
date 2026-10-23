package gt.edu.umg.desarrolloweb.pagos.domain;

/** Estado de la entrega de un pago a un destino (fila de {@code pagos_entregas}). */
public enum EstadoEntrega {
    PENDIENTE,
    EN_VUELO,
    REINTENTO,
    /** Timeout o 504: el destino pudo registrar el pago o no; antes de reenviar se consulta. */
    DESCONOCIDO,
    ENTREGADO,
    DEAD_LETTER;

    public boolean esTerminal() {
        return this == ENTREGADO || this == DEAD_LETTER;
    }
}
