# Pruebas con interfaz gráfica

Cómo probar el flujo completo sin escribir comandos (salvo levantar el entorno). El **Banco** y el
**ERP** son los simuladores reales del docente en AWS; HidroGest es la réplica local y la tercera API
es el mock local.

## Herramientas

| Herramienta | URL | Para qué | Acceso |
|---|---|---|---|
| **Adminer** | http://localhost:8081 | Ver la BD de HidroGest y **simular a la cajera** (registrar y cobrar pagos) | Sistema *MySQL*, servidor `mariadb`, usuario `hidrogest_app`, contraseña `HIDROGEST_APP_PASSWORD` de `.env`, base `hidrogest` |
| **DynamoDB Admin** | http://localhost:8001 | Estado de cada pago por destino y bitácora de intentos | Sin login |
| **RabbitMQ** | http://localhost:15672 | Colas, reintentos en espera, dead-letter | `RABBIT_USER` / `RABBIT_PASSWORD` de `.env` |
| **Postman** (o Bruno) | — | Estado del pago en el servicio y **verificación directa en el banco y el ERP** | Importar `docs/postman/*.json` |
| **Docker Desktop** | — | Detener/arrancar el mock del tercero (prueba de destino caído) | — |

**Postman, una sola vez:** *Import* → `docs/postman/pagos-sender.postman_collection.json` y
`docs/postman/pagos-sender-local.postman_environment.json`. En el entorno *pagos-sender local*
completa `adminPassword` (`ADMIN_PASSWORD`), `bankApiKey`, `erpApiKey` y `thirdApiKey` copiándolos de
tu `.env`, y selecciónalo arriba a la derecha. Esos valores quedan solo en tu Postman: **no** vuelvas
a exportar el entorno al repo.

**Levantar el entorno** (única vez que se usa la terminal):

```bash
docker compose up -d
```

## Cómo registrar o cobrar un pago (Adminer)

1. Entra a http://localhost:8081 con los datos de la tabla.
2. En el menú izquierdo baja hasta **Rutinas** y haz clic en **simular_nuevo_cobro** (si Adminer está
   en inglés: *Routines* y *Call*).
3. Clic en **Llamar**, completa `p_monto` (ej. `120.50`), `p_metodo` (`Efectivo`, `Credito` o
   `Debito`) y `p_pagado` (`1` = cobrado, `0` = pendiente). Clic en **Llamar**.
4. El resultado muestra el **`pago_id`** y su llave (`EQA-…`). Anótalo.

Para cobrar uno pendiente: **Rutinas → simular_cobro_tardio → Llamar** con `p_pago_id`.
Para ver los datos: **tb_pagos → Seleccionar datos** (están el monto, el método y `estado_pago`).

> Esto imita lo que hace la cajera en Laravel. pagos-sender **nunca** escribe en esta BD: su usuario
> solo tiene SELECT.

## Prueba 1 · Flujo completo

1. **Adminer:** `simular_nuevo_cobro(120.50, Efectivo, 1)` → anota el `pago_id` (ej. `1793143394000`; cada instalación tiene su propio rango).
2. Espera ~10 s.
3. **DynamoDB Admin → pagos_entregas:** aparecen 3 filas con ese `paymentId` (BANCO, ERP, TERCERO)
   con `estado = ENTREGADO` y su `receiptId` (`BNK-…`, `ERP-…`, `THR-…`).
4. **DynamoDB Admin → pagos_intentos:** una fila por llamada (`operacion = SEND`, `httpStatus = 201`,
   `duracionMs`).
5. **Postman:** en el entorno pon `pagoId` = ese id. Ejecuta la carpeta **2. Estado de un pago →
   Estado del pago** y luego la carpeta **3. Verificación directa en los destinos**.
   Clic derecho en la colección → *Run* para ejecutar todo de una vez.

**Debe pasar:** los 3 destinos `ENTREGADO`, `completo = true`, y en la carpeta 3 las pruebas
*"mismo comprobante que guardó el servicio"* en verde para banco y ERP: el pago está **en el sistema
del docente** con el mismo comprobante.

## Prueba 2 · Pago cobrado después

1. **Adminer:** `simular_nuevo_cobro(75.00, Debito, 0)` → anota el `pago_id`.
2. Espera 20 s. **DynamoDB Admin → pagos_entregas:** ese pago **no** aparece (no está PAGADO).
3. **Adminer:** `simular_cobro_tardio(<pago_id>)` → `resultado = cobrado ahora`.
4. En ~10 s aparecen sus 3 entregas en `ENTREGADO`. El servicio lo detectó porque cambió `updated_at`.

## Prueba 3 · Destino caído

1. **Docker Desktop → Containers → pagos-third-mock → Stop** (■).
2. **Adminer:** `simular_nuevo_cobro(47.80, Efectivo, 1)`.
3. **DynamoDB Admin:** BANCO y ERP en `ENTREGADO`; TERCERO en `REINTENTO` con
   `lastErrorCode = SIN_CONEXION` y `nextAttemptAt` en el futuro.
4. **RabbitMQ → Queues:** hay mensajes en `pagos.tercero.retry.*`.
5. **Postman → 1. Salud → Destinos y circuit breakers:** el tercero aparece `DOWN`.
6. **Docker Desktop → pagos-third-mock → Start** (▶). En el siguiente reintento (5 s, 30 s, 2 min…)
   el TERCERO pasa a `ENTREGADO`. Banco y ERP **no se reenvían**.

## Prueba 4 · Rechazo definitivo (dead-letter)

1. **Adminer:** `simular_nuevo_cobro(30000.00, Efectivo, 1)` (más de Q25,000).
2. **DynamoDB Admin:** BANCO y TERCERO en `DEAD_LETTER` con `lastErrorCode = VALIDACION_LOCAL`
   (se rechazó antes de llamar: el contrato no lo permite); ERP en `ENTREGADO` (no tiene ese límite).
3. **RabbitMQ → Queues → pagos.banco.dlq → Get messages:** el header `x-pagos-dead-reason` trae la causa.
4. **Postman → Estado del pago:** `completo = false` (correcto: un destino no puede recibirlo).

## Prueba 5 · Fallos de las APIs del docente (timeout, 503)

Requiere una línea en la terminal, porque cambia la configuración del servicio:

```bash
BANK_SIM_SCENARIO=TIMEOUT ERP_SIM_SCENARIO=ERROR_503 docker compose up -d pagos-sender
```

1. **Adminer:** `simular_nuevo_cobro(88.00, Efectivo, 1)`.
2. **DynamoDB Admin → pagos_entregas:** BANCO en `DESCONOCIDO` (`TIMEOUT`), ERP en `REINTENTO` (`503`),
   TERCERO en `ENTREGADO`. **pagos_intentos** muestra, para el banco, `SEND` con timeout seguido de
   `LOOKUP` (consulta antes de reenviar, para no duplicar).
3. Quita los escenarios: `docker compose up -d pagos-sender`. Lo pendiente se entrega solo.
4. **Postman → carpeta 3:** el banco tiene **un solo** comprobante para esa llave.

## Prueba 6 · Reinicio sin duplicados

**Docker Desktop → pagos-sender → Restart.** En DynamoDB Admin no aparecen intentos nuevos de los
pagos ya entregados (la fila `ENTREGADO` no cambia su `updatedAt`).

## Qué revisar siempre

- La llave es `EQA-<id con 6 dígitos>` y es la **misma** en todos los intentos y destinos.
- Si falla un destino, **solo ese** queda en `REINTENTO`; los demás no se tocan.
- El banco recibe el monto en **centavos** (`valueInCents`) y el ERP como **texto** con 2 decimales
  (`total`): se ve en la carpeta 3 de Postman.
- `/actuator/health` sigue `UP` aunque un destino esté caído; el detalle está en *Destinos y circuit breakers*.
- Cada cobro de prueba **queda registrado en el simulador del docente**: no reutilices ids ni cambies
  datos de un pago ya enviado (el banco respondería `IDEMPOTENCY_KEY_REUSED`).
