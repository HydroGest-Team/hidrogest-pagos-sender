package gt.edu.umg.desarrolloweb.pagos.infrastructure.destinos.common;

/** Recorte de textos a los largos máximos de cada contrato. */
public final class Texto {

    private Texto() {
    }

    public static String recortar(String valor, int max) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.length() <= max ? limpio : limpio.substring(0, max);
    }
}
