# ADR-0004 · Timeout = DESCONOCIDO: primero consultar, después reenviar

**Estado:** aceptado · 2026-10-23

## Contexto
Con `LOST_ACK` el destino **registra** el pago y luego tarda más que nuestro timeout de lectura
(3 s). Con `TIMEOUT` tarda y **no** lo registra. Desde el cliente ambos se ven igual.

## Decisión
- Timeout de lectura, error de red a mitad de la llamada, `504` o un `2xx` ilegible → estado
  `DESCONOCIDO`. Sin conexión (el request no salió) → `REINTENTO`.
- El siguiente intento de un `DESCONOCIDO` (o de un `EN_VUELO` cuyo consumer murió) hace primero
  `GET` por la llave: `200` → `ENTREGADO` con ese comprobante, sin reenviar; `404` → se envía el
  `POST` con **la misma llave**; si el `GET` falla se reprograma.
- Si aun así se reenvía algo ya registrado, el destino responde `409 DUPLICATE_PAYMENT` /
  `DOCUMENTO_DUPLICADO`, que se trata como entregado tomando el comprobante original.

## Consecuencias
Verificado contra el simulador real del banco el 2026-10-23: un `POST` que excedió 3 s quedó
`DESCONOCIDO` y el `GET` posterior devolvió el comprobante (`ENCONTRADO_EN_CONSULTA`), sin duplicar.
