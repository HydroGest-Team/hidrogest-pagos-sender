# ADR-0002 · Estado de envío en DynamoDB; HidroGest solo lectura, sin JPA

**Estado:** aceptado · 2026-10-23

## Contexto
Cada pago va a 3 destinos y cualquiera puede fallar sin saber cuál. Hay que recordar, por
destino, si ya se entregó, cuántos intentos van y cuándo toca el siguiente. La BD de HidroGest
(Laravel) es de otro equipo: no se modifica su esquema.

## Decisión
- Tres tablas DynamoDB: `pagos_entregas` (PK `paymentId`, SK `destino`), `pagos_intentos`
  (bitácora, TTL) y `pagos_control` (cursor del producer).
- Índice **disperso** `abiertos-index` (`openKey`, `nextAttemptAt` numérico): `openKey` solo existe
  mientras la entrega no es terminal, así el reconciliador consulta lo pendiente sin escanear.
- Toda transición es un `UpdateItem` **condicional por versión**; `ENTREGADO` nunca se sobrescribe y
  solo quien reclamó el envío (lease) puede cerrarlo.
- HidroGest se lee con **Spring JDBC** (`NamedParameterJdbcTemplate`), consultas `SELECT` con
  proyección, conexión `read-only` y un usuario de BD con permiso **solo SELECT**. Sin JPA.
- En local las tablas se crean al arrancar (`DYNAMO_CREAR_TABLAS=true`); en AWS los nombres son
  configurables y las crea quien administre la cuenta (pendiente D4).

## Consecuencias
- Idempotencia de extremo a extremo: llave estable + escritura condicional + ack después de persistir.
- `nextAttemptAt` y `leaseUntil` se guardan como epoch millis (N): como texto ISO de largo variable
  no se ordenan bien en el índice.
