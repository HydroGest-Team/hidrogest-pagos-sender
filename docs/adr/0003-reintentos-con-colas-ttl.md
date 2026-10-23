# ADR-0003 · Reintentos no bloqueantes con colas de espera (TTL + DLX)

**Estado:** aceptado · 2026-10-23

## Contexto
Los simuladores fallan a propósito (429, 500, 502, 503, 504, timeouts). Reintentar dentro del
listener (o con Feign/Resilience4j Retry) bloquea al consumer, se pierde si el proceso reinicia y
oculta cuántos intentos hubo.

## Decisión
- Por destino: cola principal **quorum** `pagos.<d>`, colas de espera `pagos.<d>.retry.<tier>`
  (`5s`, `30s`, `2m`, `10m`, configurables) con `x-message-ttl` que al vencer devuelven el mensaje a
  la principal, y DLQ `pagos.<d>.dlq`.
- Ante RETRY/UNKNOWN el consumer guarda el estado, publica en el tier `>= max(backoff, Retry-After)`
  y hace **ack** del original. Al llegar a `max-intentos` → `DEAD_LETTER` + DLQ.
- Sin reintentos ocultos: `Retryer.NEVER_RETRY` en Feign y `listener.retry.enabled=false`.
- **Circuit breaker por destino** (Resilience4j): con el circuito abierto no se llama a la API y el
  mensaje espera sin consumir un intento, para que un destino caído no agote a todos los pagos.
- Concurrencia por destino (listeners separados): un destino lento no frena a los otros.

## Consecuencias
- Un mensaje que llega antes de su `nextAttemptAt` (duplicado) se descarta; el reconciliador
  reencola lo que quede vencido, así que perder un mensaje no pierde el pago.
- Las colas existen aunque un destino esté inactivo; su listener no arranca.
