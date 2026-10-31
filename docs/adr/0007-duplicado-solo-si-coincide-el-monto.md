# ADR-0007 · Un duplicado solo es "entregado" si coincide el monto

**Estado:** aceptado · 2026-10-27

## Contexto
La tabla de decisión (ADR-0004) trata `409 DUPLICATE_PAYMENT` / `DOCUMENTO_DUPLICADO` y el `200`
de la consulta por llave como "el destino ya tiene este pago". El contrato reserva
`IDEMPOTENCY_KEY_REUSED` / `DOCUMENTO_REUTILIZADO` para la misma llave con datos distintos.

El 2026-10-27, contra los simuladores reales, el pago nuevo 900007 (Q142.35) recibió `409
DUPLICATE_PAYMENT` del banco y `DOCUMENTO_DUPLICADO` del ERP con comprobantes del 2026-10-23, cuya
llave `EQA-900007` estaba registrada con **Q135.25**: la réplica local de HidroGest se había
recreado y reutilizó el id. Los simuladores no detectaron la diferencia y el servicio marcó
`ENTREGADO` un pago que los destinos tienen con otro monto.

## Decisión
- Ante un duplicado o una consulta exitosa se compara el monto registrado por el destino
  (`originalReceipt.amount.valueInCents`, `comprobante_original.total`,
  `originalConfirmation.amount`, o el del `GET`) con el de HidroGest.
- Si difiere: `DEAD_LETTER` con el código local `LLAVE_REUTILIZADA` y el monto registrado en el
  detalle. No se guarda el comprobante ajeno.
- Si la respuesta no trae monto (fuera de contrato) se confía en el destino, como antes.
- Solo se compara el monto: es el único dato del pago que los tres comprobantes devuelven.

## Consecuencias
Una llave reutilizada con el **mismo** monto sigue pasando como entregada; con la BD real de
HidroGest los ids no se reutilizan, así que solo puede ocurrir entre réplicas de prueba.
Un `LLAVE_REUTILIZADA` no se resuelve reencolando: hay que limpiar los datos de prueba
(ver RUNBOOK, "Empezar de cero").

## Prevención en la réplica local
Esta regla detecta el choque, pero la causa está en la réplica: los cobros simulados seguían a la
semilla (900007, 900008…) y repetían ids al recrear el volumen o entre compañeros. Ahora
`docker/mariadb/04-simulacion-cajera.sql` arranca el `AUTO_INCREMENT` de `tb_pagos` en
`<segundos unix de la inicialización> × 1000`, un rango propio por instalación. Para que dos
réplicas choquen tendrían que inicializarse en el mismo segundo, o una tendría que registrar más
de 1000 cobros por cada segundo de diferencia entre ambas inicializaciones.

Se descartó cambiar el formato de la llave (prefijo por desarrollador): la llave de producción
debe seguir siendo `EQA-<id>`, estable y derivada del id real. Solo cambia la réplica: el servicio
y la BD real de HidroGest no se tocan.
