package gt.edu.umg.desarrolloweb.pagos.application;

/** Clave del MDC con el {@code X-Correlation-Id}: viaja productor → cola → consumer → API y en cada log. */
public final class Correlation {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private Correlation() {
    }
}
