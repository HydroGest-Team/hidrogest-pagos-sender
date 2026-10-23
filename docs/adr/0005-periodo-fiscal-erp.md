# ADR-0005 · `periodo_fiscal` del ERP = período facturado de la lectura (D6)

**Estado:** aceptado · 2026-10-23

## Contexto
El plan dejó abierto si `periodo_fiscal` es el mes del pago o el período facturado. El contrato
`erp-payments-api.yaml` lo aclara con su ejemplo: un pago del `2026-10-01` lleva
`periodo_fiscal {anio: 2026, mes: 9}` y la línea "Consumo septiembre 2026"; el comprobante trae
aparte `periodo_contable` (el mes en que el ERP lo contabiliza). Las reglas `PERIODO_CERRADO`
(más de 12 meses) y `PERIODO_FUTURO` (después del mes siguiente) se aplican sobre ese campo.

## Decisión
`periodo_fiscal` = año y mes de `tb_periodos.fecha_apertura` de la lectura pagada. El mismo valor
se usa en `description` del banco ("Servicio de agua 2026-09") y en `period` del tercero
(que su contrato define como "Período facturado").

## Consecuencias
Un pago de una lectura con más de 12 meses de antigüedad será rechazado por el ERP con
`422 PERIODO_CERRADO` y quedará en `DEAD_LETTER` para revisión manual; los otros destinos no se ven
afectados.
