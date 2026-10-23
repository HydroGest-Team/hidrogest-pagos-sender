package gt.edu.umg.desarrolloweb.pagos.domain;

import java.util.Arrays;

/** Valores de {@code tb_pagos.metodo_pago} en HidroGest. */
public enum MetodoPago {
    EFECTIVO("Efectivo"),
    CREDITO("Credito"),
    DEBITO("Debito");

    private final String valorBd;

    MetodoPago(String valorBd) {
        this.valorBd = valorBd;
    }

    public boolean esTarjeta() {
        return this == CREDITO || this == DEBITO;
    }

    public static MetodoPago desdeBd(String valor) {
        return Arrays.stream(values())
                .filter(m -> m.valorBd.equalsIgnoreCase(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no soportado: " + valor));
    }
}
