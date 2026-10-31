# Runbook · pagos-sender

Operación del servicio. Los ejemplos usan el entorno local (`docker compose`); las rutas `/admin`
piden Basic Auth con `ADMIN_USER` / `ADMIN_PASSWORD` de `.env`.

```bash
# Cargar la contraseña en la sesión sin mostrarla
export ADMIN_PASSWORD=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
```

## 1. ¿En qué va un pago?

```bash
curl -s -u admin:$ADMIN_PASSWORD http://localhost:8080/admin/pagos/900001 | jq
```

Devuelve, por destino: `estado`, `intentos`, `receiptId` (comprobante), último HTTP/código y la
bitácora de cada llamada (`SEND`/`LOOKUP`, resultado, duración). `completo: true` = entregado a
todos los destinos activos.

| Estado | Significado | ¿Qué hacer? |
|---|---|---|
| `PENDIENTE` | Creado, aún sin procesar | Nada |
| `EN_VUELO` | Un consumer lo está enviando (lease) | Nada; si el consumer muere, al vencer el lease se retoma |
| `REINTENTO` | Falló de forma transitoria; espera en una cola TTL | Nada |
| `DESCONOCIDO` | Timeout/504: pudo registrarse; el próximo intento consulta primero | Nada |
| `ENTREGADO` | Tiene comprobante del destino | — |
| `DEAD_LETTER` | Rechazo definitivo o intentos agotados | Ver sección 3 |

## 2. Salud y métricas

```bash
curl -s http://localhost:8080/actuator/health                                   # público
curl -s -u admin:$ADMIN_PASSWORD http://localhost:8080/actuator/health | jq      # con detalle
curl -s -u admin:$ADMIN_PASSWORD http://localhost:8080/actuator/health/destinos | jq
curl -s -u admin:$ADMIN_PASSWORD http://localhost:8080/actuator/prometheus | grep '^pagos_'
```

- `readiness` solo depende de BD, RabbitMQ y DynamoDB. Que un destino externo caiga **no** saca de
  servicio al proceso; se ve en el grupo `destinos` (con el estado de su circuit breaker).
- Métricas: `pagos_intentos_total{destino,operacion,resultado}`, `pagos_intentos_latencia_seconds`,
  `pagos_entregas_cerradas_total{destino,estado}`, `pagos_completos_total`,
  `pagos_entregas_abiertas{destino}`, `pagos_dlq_mensajes{destino}`.
- Consola de RabbitMQ: http://localhost:15672 (usuario/contraseña de `.env`).

## 3. Dead-letter

Causas típicas (columna `lastErrorCode`):

| Código | Causa | Corrección |
|---|---|---|
| `VALIDACION_LOCAL` | El pago no cumple el contrato (monto > Q25,000, fecha futura, etc.) | Corregir el dato en HidroGest |
| `403` sin código / `TEAM_MISMATCH` / `EQUIPO_NO_COINCIDE` | API key inválida o `TEAM_CODE` que no corresponde | Corregir `.env` y reiniciar |
| `IDEMPOTENCY_KEY_REUSED` / `DOCUMENTO_REUTILIZADO` | La misma llave ya se usó con otros datos | Revisar si el pago cambió después de enviarse |
| `LLAVE_REUTILIZADA` | El destino respondió "duplicado" (o el `GET` lo encontró) pero con **otro monto**: la llave es de otro pago (ADR-0007) | Pasa con réplicas de prueba que reutilizan ids. Reencolar no sirve: ver sección 7 |
| `PERIODO_CERRADO` / `PERIODO_FUTURO` | Período de la lectura fuera de rango para el ERP | Revisar el período en HidroGest |
| `500`/`503`… con "intentos agotados" | El destino estuvo caído más que los reintentos | Reencolar cuando vuelva |

Reencolar **una** entrega ya corregida (vuelve a `PENDIENTE` con la misma llave):

```bash
curl -s -X POST -u admin:$ADMIN_PASSWORD \
  http://localhost:8080/admin/pagos/900001/destinos/ERP/reencolar
```

Los mensajes de `pagos.<destino>.dlq` son solo informativos (el header `x-pagos-dead-reason` trae
la causa); el estado que manda es el de DynamoDB. Se pueden purgar desde la consola de RabbitMQ.

## 4. Forzar ciclos

```bash
curl -s -X POST -u admin:$ADMIN_PASSWORD http://localhost:8080/admin/producer     # leer HidroGest ya
curl -s -X POST -u admin:$ADMIN_PASSWORD http://localhost:8080/admin/reconciliar  # vencidos + barrido
```

## 5. Probar fallos (simuladores)

```bash
# Forzar escenarios en los simuladores (el docente puede deshabilitar el header)
BANK_SIM_SCENARIO=LOST_ACK ERP_SIM_SCENARIO=ERROR_503 THIRD_SIM_SCENARIO=TIMEOUT \
  docker compose up -d pagos-sender
docker/mariadb/nuevo-cobro.sh 135.25 Debito       # la "cajera" registra un cobro nuevo
docker compose logs -f pagos-sender | grep 'pago=9'
docker compose up -d pagos-sender                  # quitar escenarios: lo pendiente se entrega solo

# Destino caído por completo, sin depender del header
docker compose stop third-api-mock     # el breaker del tercero se abre; banco y ERP siguen
docker compose start third-api-mock    # al volver, lo pendiente se entrega

# Pago que pasa a PAGADO después (el producer lo detecta por updated_at)
docker/mariadb/nuevo-cobro.sh 75.00 Debito pendiente   # anota el id
docker/mariadb/simular-cobro.sh <id>
```

## 6. Activar un destino nuevo sin reenviar el historial

1. Agregarlo a `DESTINOS_ACTIVOS` y configurar su URL y API key.
2. Opcional: `pagos.desde.<DESTINO>` = marca de tiempo mínima (hora de la BD, UTC) de los pagos a enviar.
3. Reiniciar. El barrido de consistencia (`RECONCILIADOR_BARRIDO`, 10 min) crea y encola solo las
   entregas que le faltan a ese destino.

## 7. Empezar de cero tras limpiar los simuladores

Las llaves (`EQA-<id>`) dependen del id del pago en HidroGest y los simuladores compartidos
recuerdan cada llave. En la réplica local:

- La **semilla** (900001–900006) tiene siempre los mismos datos: si se reenvía, el simulador
  responde "duplicado" con el mismo monto, y es correcto tratarla como entregada.
- Los **cobros simulados** (`simular_nuevo_cobro`) toman ids de un rango propio de cada
  instalación: `<segundos unix al crear el volumen> × 1000`, por ejemplo `1793143394000`
  (llave `EQA-1793143394000`). Así no repiten llaves al recrear la réplica ni entre compañeros.

Si aparece un `LLAVE_REUTILIZADA`, el destino tiene esa llave con otro pago: viene de una réplica
anterior a este esquema de ids. Reencolar no lo arregla; se deja en dead-letter o se empieza de cero.

Cuando el docente limpie los datos de los simuladores, reiniciar también el estado local para que
ambos lados coincidan:

```bash
docker compose down -v      # borra MariaDB (réplica), DynamoDB Local y RabbitMQ
docker compose up -d        # semilla 900001-900005 de nuevo: se envían a los 3 destinos
```

Sin ese reinicio, DynamoDB Local seguiría marcando como `ENTREGADO` pagos que los simuladores ya
no tienen, y no se volverían a enviar.

Una réplica creada antes de este cambio sigue numerando desde 900007. Para pasarla al rango propio
sin borrar datos, aplicar de nuevo el script (es idempotente: ajusta el contador y reemplaza las rutinas):

```bash
docker exec -i pagos-mariadb sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' \
  < docker/mariadb/04-simulacion-cajera.sql
```
