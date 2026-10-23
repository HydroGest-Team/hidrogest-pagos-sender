# Feature 3 — Envío de pagos a sistemas externos
**Proyecto:** Oficina del Agua · Fase 2 · Desarrollo Web 2026 (UMG Jutiapa)
**Documento:** contexto completo + plan de ejecución para que un chat nuevo construya la solución de principio a fin.
**Idioma de trabajo:** español (código, nombres de clases y mensajes de commit en inglés está bien; comentarios, README y explicaciones en español).

> **Regla de oro para quien lea esto:** los archivos YAML de los contratos son la **fuente de verdad**. Este documento los resume para dar contexto, pero si algo aquí contradice un YAML, **gana el YAML**. Nunca modificar los YAML entregados por el docente.

---

## 1. Objetivo

Construir un **microservicio independiente en Spring Boot** que traslada los **pagos cobrados** de la Oficina del Agua (base de datos de HidroGest) hacia **3 sistemas externos** (APIs REST), de forma **confiable, idempotente y observable**, con arquitectura **productor / consumidor sobre RabbitMQ**.

Requisito de negocio clave:

> Cada pago se envía a **3 APIs**. En cualquier momento una puede fallar y **no se sabe cuál**. El sistema debe **verificar** qué destinos ya recibieron el pago y **reenviar solo a los que faltan**, de manera inteligente (sin duplicar en los que ya lo tienen).

Meta final: una solución **completamente funcional y completa**, siguiendo **las mejores prácticas de programación** (ver sección 13).

---

## 2. Alcance

| Dentro del alcance | Fuera del alcance |
|---|---|
| Este módulo: Feature 3 (envío de pagos). Es un **microservicio independiente**. | Feature 1 (consulta pública con captcha), Feature 2 (chatbot RAG), Feature 4 (replicación a NoSQL). No depender de ellas. |
| Leer pagos de la BD existente de HidroGest (solo lectura). | Modificar el esquema o el código de HidroGest (Laravel). |
| Estado de envío por destino en **DynamoDB**. | Construir **Lambdas** (decisión del usuario: no usarlas si todo se puede hacer sin ellas). |
| Clientes Feign para las 3 APIs, producer, consumer, reconciliador. | Implementar las APIs externas reales (las provee el docente). |

---

## 3. Fuentes de contexto

### 3.1 Diagrama de arquitectura (imagen "Oficina de agua")
Muestra 4 funcionalidades. La **3** es la nuestra:

```
pagos pendientes → producer → cola → consumer ──HTTP──► Bancos / sistemas externos
                                         │
                                         ├─► DynamoDB Logs (procesos exitosos/fallidos)
                                         └─► (en el diagrama) Lambda → flag "enviado" en MySQL
```
- El **producer** consulta pagos pendientes "por medio de flag".
- El **consumer** llama a los sistemas externos.
- El diagrama dibuja una **Lambda** que marca el flag; **decisión tomada: no se construye**. El consumer escribe el resultado directamente en DynamoDB.
- El diagrama dibuja "MySQL en EC2 + PHP"; en la práctica es la base MariaDB/MySQL de **HidroGest**.

### 3.2 Diapositiva Clase 10 — "Las 4 features de la Fase 2"
> **03 · Envío de pagos a sistemas externos — Spring Boot + RabbitMQ · productor / consumidor**
Por tanto la cola es **RabbitMQ** (no SQS, aunque el icono del diagrama se parezca).
Otras features: 01 Next.js + microservicio Spring Boot (solo lectura); 02 Next.js BFF + Spring Boot + pgvector + LLM externo; 04 Spring Boot + DynamoDB/MongoDB, "diff sin borrar".

### 3.3 Apuntes del usuario (requisitos explícitos)
1. Usar **FeignClient** (Spring Cloud OpenFeign).
2. **No generar los Specs/interfaces de las APIs; generar solo los DTO** (modelos).
3. **JDK 17**.
4. **Spring Boot**.
5. Crear **un repositorio nuevo**, pero usando **la misma base de datos** que ya existe: https://github.com/HydroGest-Team/HydroGest
6. Se entregaron 2 contratos OpenAPI (banco y ERP) para el traslado de pagos. Las APIs funcionando se esperan **la semana siguiente al 14-oct-2026**; con el contrato se avanza mientras tanto.
7. Cada módulo/microservicio es **independiente**; el equipo se centra solo en el de pagos.
8. DynamoDB se usa porque **guarda cuáles pagos ya se enviaron y cuáles no**.
9. **Sin Lambdas** si se puede hacer todo sin ellas.
10. Sobre `X-Simulation-Scenario`: decisión delegada — "que todo funcione correctamente, siguiendo buenas prácticas".
11. La **tercera API no fue entregada**: se resuelve con un contrato provisional + mock (sección 6.3).

### 3.4 Archivos que acompañan este documento
| Archivo | Para qué |
|---|---|
| `bank-payments-api.yaml` | Contrato oficial del **Banco** (no modificar) |
| `erp-payments-api.yaml` | Contrato oficial del **ERP** (no modificar) |
| `third-payments-api.yaml` | Contrato **provisional** de la tercera API (propio del equipo) |
| `mock_third_api.py`, `test_scenarios.sh` | Mock verificado de la tercera API y su prueba de escenarios |
| `api-bancos-feature3.md` | Guía de integración del banco (borrador de clase: reglas, tabla de respuestas, escenarios) |
| `Desarrollo_Web_2026_-_Oficina_de_agua.jpeg` | Diagrama de arquitectura |
| `WhatsApp_Image_2026-10-14...jpeg` | Foto de la diapositiva "4 features de la Fase 2" |

---

## 4. Datos conocidos del entorno

| Dato | Valor |
|---|---|
| Banco — base URL | `https://gdsi5lyj34.execute-api.us-east-1.amazonaws.com/prod` (apiId `gdsi5lyj34`, región `us-east-1`, stage `prod`) |
| Banco — autenticación | Header **`x-api-key`** (NO `Authorization`). La llave se pasa por variable de entorno `BANK_API_KEY`. **No escribirla en el repo ni en logs.** |
| Llave de ejemplo del docente | `EQA-BRUNO-001` (solo una llave de prueba para `GET`) |
| `teamCode` | **Por confirmar**. Default `EQUIPO_A` (sugerido por el prefijo `EQA-`). Configurable con `TEAM_CODE`. El **prefijo se deriva**: `EQUIPO_A` → `EQA-` |
| ERP | Se asume mismo `apiId`; rutas `/erp/v1/pagos`, `/erp/v1/salud`. Base URL y llave **configurables aparte** (`ERP_BASE_URL`, `ERP_API_KEY`) |
| Simulador banco/ERP | Corren en **AWS del docente** (API Gateway + Lambda + DynamoDB), compartidos por los 4 equipos. **No** van en nuestro Docker Compose. Existe `sam local start-api` en `http://localhost:3000` (opcional) |
| Versiones usadas en clase | Spring Boot **3.5.6**, JDK 17, `openapi-generator-maven-plugin` **7.12.0**, Spring Cloud **2025.0.x**, Maven |
| BD de pagos | MariaDB/MySQL de HidroGest (Laravel 11). `.env.example` de HidroGest usa SQLite por defecto: nuestro servicio necesita la **MariaDB real** |

**Cómo confirmar el `teamCode`** (lo hace el usuario en su máquina; el chat no tiene acceso a AWS): enviar un `POST` de prueba con `X-Simulation-Scenario: SUCCESS`, llave `EQA-PRUEBA-001`, monto 100 centavos. `201` ⇒ el `teamCode` es correcto; `403 TEAM_MISMATCH` ⇒ probar `EQUIPO_B/C/D`; `403 Forbidden` genérico ⇒ llave inválida.

---

## 5. Esquema real de HidroGest relevante (migraciones de Laravel, rama principal)

> El servicio **solo lee**. Nunca crear tablas ni migraciones en esa BD. Verificar contra el repo antes de fijar nombres (el esquema puede evolucionar).

**`tb_pagos`**: `id`, `monto_pago` DECIMAL(10,2), `fecha_pago` DATETIME (sin zona), `metodo_pago` ENUM('Efectivo','Credito','Debito'), `estado_pago` ENUM('PAGADO','NO PAGADO') default 'NO PAGADO', `lecturas_id` (único, FK), `usuario_id` (FK), `created_at`, `updated_at`.
**`tb_lecturas`**: `id`, `numero_recibo` VARCHAR(30) único, `lectura_anterior`, `lectura_actual`, `consumo` (generada), `monto` DECIMAL(10,2), `fecha_lectura`, `tarifa_id`, `usuario_id`, `contador_id`, `periodo_id`, timestamps.
**`tb_contadores`**: `id`, `codigo_contador` VARCHAR(20), `sector_contador`, `activo_contador`, `fecha_instalacion`, `cliente_id` (FK), timestamps.
**`tb_clientes`**: `id`, `dpi_cliente` VARCHAR(13) único, `nombre1_cliente`, `nombre2_cliente`, `nombre3_cliente`, `apellido1_cliente`, `apellido2_cliente`, `apellido3_cliente`, `telefono_cliente`, `direccion_cliente`, `numero_cuenta_cliente`, `activo_cliente`, timestamps.
**`tb_periodos`**: `id`, `fecha_apertura`, `fecha_cierre`, `estado_periodo` ('ACTIVO','CERRADO'), timestamps.
**`tb_tarifas`** / **`tb_tipo_tarifa`**: tarifas y su tipo (no necesarias para el envío).
**`tb_usuarios`** (`id`, `name`, `email`, `password`, `role_id`) y **`tb_roles`**: solo existen por las FK.

Camino de datos: `tb_pagos → tb_lecturas → tb_contadores → tb_clientes`, y `tb_lecturas → tb_periodos`.

Observaciones críticas:
- **No existe ninguna columna de "flag enviado"**. El estado de envío vive en DynamoDB.
- `estado_pago` nace en `NO PAGADO` y puede pasar a `PAGADO` **después**. Un pago con `id` menor al ya procesado puede volverse `PAGADO` más tarde ⇒ **no usar un cursor por `id`**; usar `updated_at` con traslape (sección 8.3).
- `fecha_pago` es DATETIME **sin zona**: interpretarlo en **`America/Guatemala`** (UTC-6, sin horario de verano).
- Solo existen métodos Efectivo/Crédito/Débito: **no hay cheque ni transferencia** en esta base.
- No hay NIT en `tb_clientes` (solo DPI): enviar `CF`. **Nunca enviar el DPI** a sistemas externos.

---

## 6. Los 3 contratos

### 6.1 Banco (`bank-payments-api.yaml`, OpenAPI 3.0.3 v1.0.0)
| Método | Ruta | operationId | Uso |
|---|---|---|---|
| POST | `/bank/v1/payments` | `sendBankPayment` | Registrar pago |
| GET | `/bank/v1/payments/{idempotencyKey}` | `getBankPayment` | Reconciliar (¿ya está?) |
| GET | `/bank/v1/health` | `getBankHealth` | Salud (sin API key) |

Headers: `x-api-key` (obligatorio salvo health), `Idempotency-Key` (obligatorio en POST; 8–64 chars, `^[A-Za-z0-9_-]+$`), `X-Correlation-Id`, `X-Simulation-Scenario` (solo pruebas).
Body `BankPaymentRequest`: `teamCode`, `payer{customerCode, fullName≤150, taxId≤20}`, `amount{valueInCents (entero 1–99,999,999), currency=GTQ}`, `transactionDate` (ISO-8601 con zona), `channel` (CASH|CARD|BANK_TRANSFER|CHECK), `description≤140`, `metadata` (máx 10).
Respuesta `201` `BankPaymentReceipt`: `receiptId` (guardar), `idempotencyKey`, `teamCode`, `status=ACCEPTED`, `amount`, `receivedAt`, `valueDate`.
Reglas: monto máx **2,500,000 centavos (Q25,000)**; fecha no más de 5 min en el futuro; `CHECK` rechazado (422 `UNSUPPORTED_CHANNEL`). Timeouts recomendados: connect 2 s, read 3 s.

### 6.2 ERP (`erp-payments-api.yaml`, OpenAPI 3.0.3 v1.0.0)
| Método | Ruta | operationId | Uso |
|---|---|---|---|
| POST | `/erp/v1/pagos` | `registrarPagoErp` | Registrar pago |
| GET | `/erp/v1/pagos/{numeroDocumento}` | `consultarPagoErp` | Reconciliar |
| GET | `/erp/v1/salud` | `consultarSaludErp` | Salud |

Estilo: español, snake_case. **Sin `Idempotency-Key`**: la unicidad va en el body (`documento_origen.numero`). Body `ErpRegistroPago`: `equipo`, `contribuyente{codigo_cliente, codigo_contador, nit}`, `periodo_fiscal{anio, mes}`, `documento_origen{tipo (RECIBO_CAJA, BOLETA_DEPOSITO o VOUCHER_TARJETA), numero (máx. 40, patrón A-Za-z0-9_-), fecha_emision (date)}`, `forma_pago` (EFECTIVO|TARJETA|TRANSFERENCIA|CHEQUE), `fecha_pago`, `total` (**texto** con 2 decimales), `detalle[]{rubro (AGUA_POTABLE|RECONEXION|MORA|NUEVA_CONEXION|OTROS), descripcion≤120 (obligatoria si OTROS), monto}`, `observaciones≤250`. **La suma de `detalle[].monto` debe igualar `total`.**
Respuesta `201` `ErpComprobante`: `numero_comprobante` (guardar), `numero_documento_origen`, `equipo`, `estado=CONTABILIZADO`, `total`, `fecha_registro`, `periodo_contable`, `numero_partida`.
Errores (`ErpError`): `estado_http`, `codigo_error` (`VALIDACION`, `EQUIPO_NO_COINCIDE`, `DOCUMENTO_NO_ENCONTRADO`, `DOCUMENTO_DUPLICADO`, `DOCUMENTO_REUTILIZADO`, `PERIODO_CERRADO`, `PERIODO_FUTURO`, `TOTAL_NO_CUADRA`, `DESCRIPCION_REQUERIDA`, `LIMITE_SOLICITUDES`, `ERROR_INTERNO`, `SERVICIO_NO_DISPONIBLE`), `mensaje`, `reintentable`, `id_correlacion`, `fecha_hora`, `errores[]`, `comprobante_original` (en duplicado).
Perfil: falla menos que el banco pero es **más lento** ⇒ leer en el YAML los timeouts recomendados del ERP y usarlos (pueden ser mayores que los del banco).

### 6.3 Tercera API — **contrato provisional** (`third-payments-api.yaml`)
La API real no fue entregada. Se creó un sustituto **propio** (marcado como provisional) más un **mock verificado** (`mock_third_api.py`, Python estándar, puerto 8089). Rutas: `POST /third/v1/payments`, `GET /third/v1/payments/{idempotencyKey}`, `GET /third/v1/health`. Estilo deliberadamente distinto: monto como texto `"150.75"`, `period` `"YYYY-MM"`, `reference` = número de recibo, `customer{code,name}`, `method` CASH|CARD, errores `{status, code, message, retryable, correlationId, timestamp, originalConfirmation}`. Idempotencia por header `Idempotency-Key`.
El mock fue probado con: 201, 409 DUPLICATE_PAYMENT, 409 IDEMPOTENCY_KEY_REUSED, GET 200/404, 400, 422, 403 TEAM_MISMATCH, 429/503 con `Retry-After`, 500, TIMEOUT (no registra), LOST_ACK (registra y tarda) y SLOW.
**Cuando llegue la API real:** reemplazar el YAML, regenerar DTO, ajustar **solo** el adaptador del destino `TERCERO`.

### 6.4 Tabla de decisión por respuesta (aplica a los 3 destinos)
Decidir **por código HTTP y por `code`/`codigo_error`**, nunca por `message`/`mensaje`, y nunca solo por `retryable` (es una pista). Los errores de API Gateway (`403` por llave, `429`, `502`, `504`) devuelven `{"message":"..."}` sin los campos del contrato: el decodificador debe tolerarlo.

| Respuesta | Resultado | Acción |
|---|---|---|
| `201` | **DELIVERED** | Guardar comprobante (`receiptId` / `numero_comprobante` / `confirmationId`), estado `ENTREGADO` |
| `409` duplicado (`DUPLICATE_PAYMENT` / `DOCUMENTO_DUPLICADO`) | **DELIVERED** | Tratar como entregado; tomar comprobante del original |
| `409` reutilizado (`IDEMPOTENCY_KEY_REUSED` / `DOCUMENTO_REUTILIZADO`) | **DEAD_LETTER** | Bug nuestro: sin reintento |
| `400` | **DEAD_LETTER** | Revisar mapeo / detalles |
| `403` (llave inválida, `TEAM_MISMATCH`/`EQUIPO_NO_COINCIDE`) | **DEAD_LETTER + alerta** | Configuración |
| `422` (límite de monto, fecha futura, canal no soportado, `TOTAL_NO_CUADRA`, `DESCRIPCION_REQUERIDA`, `PERIODO_CERRADO`, `PERIODO_FUTURO`) | **DEAD_LETTER** | Regla de negocio: sin reintento |
| `429`, `500`, `502`, `503` | **RETRY** | Backoff; respetar `Retry-After` (`429`/`503`) |
| `504`, timeout, error de red | **UNKNOWN** | Pudo registrarse o no: **primero consultar (`GET`)**; si no está, reintentar el `POST` |
| `404` en `GET` de reconciliación | no existe | Enviar el `POST` |

---

## 7. Mapeos HidroGest → cada destino

**Llave estable por pago:** `<prefijo>-<id con 6 ceros>` = `EQA-000123` (prefijo derivado de `TEAM_CODE`). Misma llave para todos los reintentos y en los 3 destinos (Banco: header `Idempotency-Key`; ERP: `documento_origen.numero`; Tercero: header). Nunca un UUID nuevo por intento.
**Dinero:** `BigDecimal` siempre (nunca `double`). Centavos: `monto.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()`.
**Elegible:** `tb_pagos.estado_pago = 'PAGADO'`.

| Campo | Banco | ERP | Tercero (provisional) |
|---|---|---|---|
| Unicidad | `Idempotency-Key` = llave | `documento_origen.numero` = llave | `Idempotency-Key` = llave |
| Equipo | `teamCode` = `TEAM_CODE` | `equipo` = `TEAM_CODE` | `teamCode` = `TEAM_CODE` |
| Cliente | `payer.customerCode` = `CLI-<tb_clientes.id>` | `contribuyente.codigo_cliente` = igual | `customer.code` = igual |
| Nombre | `payer.fullName` = nombres+apellidos concatenados (≤150) | — | `customer.name` |
| NIT | `payer.taxId` = `CF` | `contribuyente.nit` = `CF` | — |
| Contador | — | `contribuyente.codigo_contador` = `tb_contadores.codigo_contador` | — |
| Monto | `amount.valueInCents` (long), validar `1 ≤ x ≤ 2,500,000` | `total` = texto 2 decimales | `amount` = texto 2 decimales (≤ 25000.00) |
| Moneda | `GTQ` | implícita | `GTQ` |
| Fecha | `transactionDate` = `fecha_pago` en `America/Guatemala`, ISO con offset | `fecha_pago` igual; `documento_origen.fecha_emision` = fecha | `paidAt` igual |
| Método | Efectivo→`CASH`; Credito/Debito→`CARD` | `forma_pago`: Efectivo→`EFECTIVO`; Credito/Debito→`TARJETA` | Efectivo→`CASH`; Credito/Debito→`CARD` |
| Tipo documento | — | Efectivo→`RECIBO_CAJA`; Credito/Debito→`VOUCHER_TARJETA` | — |
| Período | `description` = "Servicio de agua AAAA-MM" | `periodo_fiscal{anio,mes}` (ver decisión D6) | `period` = `AAAA-MM` del período de la lectura |
| Detalle | — | **una línea** `AGUA_POTABLE`, `monto = total`, `descripcion` = "Consumo <mes año>" (la suma siempre cuadra) | — |
| Recibo | `metadata.sourceSystem` = `oficina-agua-pagos` | `observaciones` con `numero_recibo` | `reference` = `tb_lecturas.numero_recibo` (≤30) |

**Validaciones previas (antes de gastar una llamada de red):** monto en rango, fecha no futura, método soportado, llave con formato válido, `total == suma(detalle)`. Si falla ⇒ `DEAD_LETTER` con código `VALIDACION_LOCAL` y registro del motivo.

---

## 8. Arquitectura objetivo

### 8.1 Visión general
```
 HidroGest (MariaDB, SOLO LECTURA)
          │  Producer (scheduler)
          ▼
 DynamoDB: crea estado PENDIENTE por (pago, destino) ──► publica 1 mensaje por (pago, destino)
          │
          ▼
 RabbitMQ  exchange "pagos" ──► cola por destino ──► Consumer ──Feign──► API destino
          ▲                         │  │
          │  colas de reintento     │  └─► DynamoDB: estado + intentos
          │  (TTL) / DLQ            │
          └─────────────────────────┘
 Reconciliador (scheduler): reencola SOLO destinos faltantes / vencidos / atascados
```
Un mensaje **por (pago, destino)**: un destino caído no afecta a los otros.

### 8.2 Estado en DynamoDB (tres tablas)
**`pagos_entregas`** — estado por (pago, destino)
- PK `paymentId` (N), SK `destino` (S: `BANCO`|`ERP`|`TERCERO`).
- Atributos: `estado` (`PENDIENTE`, `EN_VUELO`, `REINTENTO`, `DESCONOCIDO`, `ENTREGADO`, `DEAD_LETTER`), `intentos`, `nextAttemptAt`, `leaseUntil`, `idempotencyKey`, `receiptId`, `lastHttpStatus`, `lastErrorCode`, `createdAt`, `updatedAt`, `version`, `openKey`.
- **GSI disperso** `abiertos-index`: PK `openKey` (= `"OPEN"` solo mientras el estado NO es terminal; se elimina al pasar a `ENTREGADO`/`DEAD_LETTER`), SK `nextAttemptAt`. Así el reconciliador encuentra pendientes sin escanear la tabla.
- **Escrituras condicionales** (`attribute_not_exists`, comparación de `estado`/`version`): solo un consumer reclama un envío (`→ EN_VUELO` con `leaseUntil`); `ENTREGADO` nunca se sobrescribe.

**`pagos_intentos`** — bitácora (el "DynamoDB Logs" del diagrama): PK `paymentId#destino`, SK `timestamp#intento`; atributos `estado` (EXITOSO/FALLIDO), `httpStatus`, `codigoError`, `correlationId`, `receiptId`, `duracionMs`. Un registro por **cada** intento. TTL opcional.

**`pagos_control`** — items de control (p. ej. cursor del producer).

Quién crea las tablas es una **decisión pendiente** (D4). El código debe poder crearlas si no existen (perfil dev/local) y respetar nombres configurables.

### 8.3 Producer
Scheduler (`fixedDelay` configurable, p. ej. 10 s):
1. Lee el cursor (`updated_at` máximo procesado) de `pagos_control`.
2. Consulta pagos `PAGADO` con `updated_at >= cursor − traslape` (p. ej. 10 min), ordenados por `(updated_at, id)`, en lotes (`LIMIT`).
3. Para cada pago y cada destino **activo**: `PutItem` condicional (`attribute_not_exists`). Solo si **se creó**, publica el mensaje con **publisher confirm**.
4. Avanza el cursor al terminar el lote.
Releer es inofensivo gracias al put condicional. Si el estado se creó pero el proceso cayó antes de publicar, el reconciliador lo detecta (PENDIENTE sin actividad por X minutos).

### 8.4 RabbitMQ
- Exchange `pagos.exchange` (direct o topic); claves `pagos.banco`, `pagos.erp`, `pagos.tercero`.
- Colas **quorum** durables: `pagos.<destino>`; DLX `pagos.dlx` y `pagos.<destino>.dlq`.
- **Reintentos no bloqueantes:** colas de espera `pagos.<destino>.retry.<tier>` (p. ej. 5 s / 30 s / 2 min / 10 min) con `x-message-ttl` y dead-letter de vuelta al exchange. Al fallar con RETRY, el consumer **elige el tier ≥ `Retry-After`** (o el siguiente por número de intento), publica en esa cola, incrementa el header `x-attempt` y **hace ack** del original. Al superar `maxAttempts`: DLQ + estado `DEAD_LETTER`. (Alternativa más simple si falta tiempo: `spring.rabbitmq.listener.simple.retry` con `AmqpRejectAndDontRequeueException`; se pierde robustez ante reinicios.)
- Mensaje: `{paymentId, destino, attempt, correlationId}` (sin datos personales: el consumer vuelve a leer el pago de la BD).
- Publisher confirms + `mandatory`; consumer con **ack manual**, `prefetch` bajo (1–5; llamadas de hasta 3 s), concurrencia configurable **por destino** (aislamiento tipo bulkhead).

### 8.5 Consumer (máquina de estados) — por mensaje
1. Cargar estado. Si `ENTREGADO` ⇒ ack y salir. Si `DEAD_LETTER` ⇒ ack (solo se reencola manualmente).
2. **Reclamar** (condicional): `PENDIENTE|REINTENTO|DESCONOCIDO|EN_VUELO con lease vencido → EN_VUELO` (`leaseUntil = ahora + timeout`). Si falla el reclamo ⇒ otro consumer lo tiene: ack.
3. Leer el pago de MariaDB (solo lectura) y **mapear + validar** (sección 7). Fallo ⇒ `DEAD_LETTER` (`VALIDACION_LOCAL`).
4. Si el estado previo era `DESCONOCIDO`: **`lookup` por llave**. Existe ⇒ `ENTREGADO` (guardar comprobante); no existe ⇒ seguir; error en la consulta ⇒ RETRY.
5. **`send`** vía el gateway del destino ⇒ clasificar (sección 6.4).
6. Registrar el intento en `pagos_intentos` y actualizar el estado (condicional). Ack.
7. **Circuit breaker por destino** (Resilience4j): con el breaker abierto no se llama a la API; el mensaje va a una cola de espera **sin consumir un intento** (el destino caído no quema intentos de todos los pagos).
Garantía: si el proceso cae entre la respuesta `201` y el guardado en DynamoDB, el mensaje se redelivera, el destino responde `409 duplicado` y se trata como éxito (idempotencia de extremo a extremo).

### 8.6 Reconciliador
Scheduler (p. ej. cada 1–5 min):
- Busca en el GSI estados abiertos con `nextAttemptAt <= ahora` y `EN_VUELO` con lease vencido, y **reencola solo esos destinos**.
- Detecta `PENDIENTE` sin mensaje (caída entre put y publish).
- **Barrido de consistencia** periódico: pagos `PAGADO` en MariaDB sin registro de estado para algún destino **activo** ⇒ crea el estado y encola solo el faltante. Esto también hace la **puesta al día automática** cuando se activa un destino nuevo (respetando `desde` configurable para no reenviar todo el historial).
- `DEAD_LETTER` **no** se reintenta automáticamente: operación manual (endpoint admin protegido o comando) para reencolar tras corregir la causa.
- Un pago está **completo** cuando **todos los destinos activos** están `ENTREGADO` (exponer como métrica).

### 8.7 Destinos enchufables (para cubrir la tercera API)
Puerto común `DestinationGateway`: `send(payment)`, `lookup(idempotencyKey)`, `health()`, `destino()`, `supportsLookup()`. Cada API (Banco, ERP, Tercero) es **un adaptador**: DTOs generados + cliente Feign escrito a mano + mapper + decodificador de errores. La lista de destinos activos va en configuración (`pagos.destinos.activos: [BANCO, ERP, TERCERO]`). Agregar/quitar un destino **no toca** producer, consumer, estado ni reconciliador. Si un destino no tiene `GET` (`supportsLookup=false`), la verificación tras un timeout se apoya en reenviar con la misma llave.

### 8.8 Estructura de paquetes (hexagonal) — base `gt.edu.umg.desarrolloweb.pagos`
```
domain/          Payment (snapshot inmutable), Destino, EstadoEntrega, Outcome, puertos:
                 PaymentSource, DeliveryStateStore, AttemptLog, DeliveryPublisher, DestinationGateway
application/     PaymentDispatcher (producer), DeliveryProcessor (consumer), ReconciliationService,
                 ResponseClassifier, RetryPolicy
infrastructure/
  mariadb/       JdbcTemplate (consultas de solo lectura, proyecciones), sin JPA
  dynamodb/      adaptadores del estado, intentos y control (AWS SDK v2 Enhanced Client)
  rabbit/        topología (Declarables), publisher con confirms, listeners, retry tiers
  destinos/
    banco/       DTO generados (target/), FeignClient manual, mapper, error decoder, gateway
    erp/         ídem
    tercero/     ídem
  config/        @ConfigurationProperties validadas, Feign, Resilience4j, scheduling
  admin/         (opcional) endpoint protegido para reencolar DLQ
```

---

## 9. Stack y generación de DTO

- **Java 17**, **Spring Boot 3.5.x** (clase usó 3.5.6), **Maven**.
- Dependencias (start.spring.io): Spring Web, **Spring Cloud OpenFeign**, **Spring for RabbitMQ**, JDBC (`spring-boot-starter-jdbc`), driver **MariaDB** (`org.mariadb.jdbc:mariadb-java-client`), Validation, Actuator, (opcional) Lombok. Además: AWS SDK v2 (`dynamodb-enhanced`), Resilience4j, Micrometer. Pruebas: JUnit 5, Mockito, AssertJ, Testcontainers (RabbitMQ, MariaDB, LocalStack o DynamoDB Local), WireMock, Awaitility.
- BOM de Spring Cloud compatible con Boot 3.5 (línea `2025.0.x`).
- **DTO únicamente.** Configurar `openapi-generator-maven-plugin` (7.12.0) con una ejecución por contrato:
  - `generatorName=spring`, `generateApis=false`, `generateModels=true`, `generateSupportingFiles=false`, `modelPackage=...destinos.<x>.dto`, `modelNameSuffix=Dto`.
  - `configOptions`: `useSpringBoot3=true`, `useBeanValidation=true`, `openApiNullable=false`, `dateLibrary=java8`.
  - YAML copiados **sin editar** a `src/main/resources/openapi/`; salida en `target/generated-sources/` (no se versiona ni se edita). Si algo no sirve, se cambia la **configuración del plugin**, nunca el YAML ni el código generado.
  - Las interfaces `@FeignClient` se escriben **a mano**; la API key y `X-Correlation-Id` se agregan con un `RequestInterceptor` por cliente.
  - Verificar con `mvn clean compile` y revisar `target/generated-sources/`. Ojo: los nombres de propiedades de los modelos ERP vienen en snake_case (mapear con `@JsonProperty` generado).
- Timeouts Feign: connect 2 s / read 3 s para banco y tercero; para el ERP usar lo que diga su YAML. **Sin reintentos de Feign** (`Retryer.NEVER_RETRY`): el reintento lo gobierna la cola.

---

## 10. Configuración (variables de entorno, sin secretos en el repo)

```yaml
pagos:
  team-code: ${TEAM_CODE:EQUIPO_A}          # prefijo EQA- derivado
  destinos:
    activos: [BANCO, ERP, TERCERO]
    tercero-desde: ${TERCERO_DESDE:}        # fecha de corte para puesta al día
  producer:
    intervalo: 10s
    lote: 200
    traslape: 10m
  reintentos:
    max-intentos: 6
    tiers: [5s, 30s, 2m, 10m]
  reconciliador: { intervalo: 2m, lease: 30s, pendiente-sin-actividad: 5m }
banco:   { base-url: ${BANK_BASE_URL},  api-key: ${BANK_API_KEY},  simulation-scenario: ${BANK_SIM_SCENARIO:} }
erp:     { base-url: ${ERP_BASE_URL:${BANK_BASE_URL}}, api-key: ${ERP_API_KEY:}, simulation-scenario: ${ERP_SIM_SCENARIO:} }
tercero: { base-url: ${THIRD_BASE_URL:http://localhost:8089}, api-key: ${THIRD_API_KEY:dev-key}, simulation-scenario: ${THIRD_SIM_SCENARIO:} }
spring:
  datasource: { url: ${DB_URL}, username: ${DB_USER}, password: ${DB_PASSWORD} }  # usuario SOLO SELECT
  rabbitmq:   { host: ${RABBIT_HOST:localhost}, username: ${RABBIT_USER}, password: ${RABBIT_PASSWORD} }
aws: { region: ${AWS_REGION:us-east-1}, dynamodb-endpoint: ${DYNAMO_ENDPOINT:} }  # endpoint vacío = AWS real
server.error.include-stacktrace: never
```
`X-Simulation-Scenario`: **detrás de propiedad y vacío por defecto**; nunca fijo en código. El docente puede deshabilitarlo el día de la demo, así que se necesita además un perfil **"destino caído"** (`base-url` inexistente) para demostrar reintentos y DLQ sin depender del header.

---

## 11. Decisiones tomadas y pendientes

| # | Tema | Estado |
|---|---|---|
| D1 | Cola | **RabbitMQ** (diapositiva de clase) |
| D2 | Lambda | **No se construye**; el consumer escribe en DynamoDB |
| D3 | Estado de envío | **DynamoDB**; HidroGest solo lectura, sin tablas ni migraciones nuestras |
| D4 | Quién crea las tablas DynamoDB | **PENDIENTE**: ¿las crea el docente o nosotros en nuestra cuenta? Mientras tanto: nombres configurables y creación automática en perfil dev/local |
| D5 | `teamCode` | **PENDIENTE de confirmar** (default `EQUIPO_A`, ver sección 4) |
| D6 | Semántica de `periodo_fiscal` del ERP (¿mes del pago o período facturado?) | **A decidir leyendo la descripción del YAML**. Si no aclara: mes de `fecha_pago` (cuándo se contabiliza). Documentar en un ADR. |
| D7 | ERP: `apiId` y llave propios | **PENDIENTE**; se asume mismo `apiId` y se deja configurable |
| D8 | Tercera API | **Provisional** (sección 6.3); reemplazar al recibir la real |
| D9 | `X-Simulation-Scenario` | Configurable, apagado por defecto; perfil "destino caído" para la demo |
| D10 | Pagos con cheque | No aplica en esta base (no existe ese método) |
| D11 | Acceso a BD | Solo lectura, sin JPA (Spring JDBC), usuario con permiso `SELECT` |

---

## 12. Plan de ejecución por fases

Cada fase termina con **código compilando, pruebas pasando y un breve resumen** de lo hecho y de lo verificado. No avanzar a la siguiente si la anterior no cumple su criterio.

| Fase | Entregable | Criterio de aceptación |
|---|---|---|
| **0. Preparación** | Leer todo; verificar el entorno (`java -version`, `mvn -v`, acceso a Maven Central, Docker). Listar supuestos y dudas. | Resumen de lo entendido + estado del entorno. Si no hay red/Maven, avisar y trabajar en modo "código sin compilar" (ver sección 14). |
| **1. Esqueleto** | Proyecto Maven (Boot 3.5.x, JDK 17), YAML copiados, plugin generando **solo DTO** de los 3 contratos, `.gitignore`, estructura de paquetes. | `mvn clean compile` OK; DTO en `target/generated-sources` |
| **2. Dominio y puertos** | Modelos, `Destino`, estados, `Outcome`, puertos, `@ConfigurationProperties` validadas. | Pruebas unitarias del dominio |
| **3. Lectura de HidroGest** | Adaptador JDBC de solo lectura (consulta de elegibles con joins, lectura de un pago completo) + esquema de pruebas derivado de la sección 5 + datos semilla. | Prueba de integración con MariaDB (Testcontainers) |
| **4. Mappers y validaciones** | Mapper por destino (sección 7) + validaciones previas. | Pruebas: redondeo a centavos, límite Q25,000, zona horaria, suma del detalle ERP, llave con formato válido, método→canal |
| **5. Clientes Feign y gateways** | 3 `@FeignClient` manuales, interceptores (API key, correlation id), decodificador de errores tolerante, `ResponseClassifier` (tabla 6.4). | Pruebas tabla-dirigidas para **todos** los códigos + WireMock (incluye cuerpo sin formato de contrato) |
| **6. Estado en DynamoDB** | Adaptadores de estado/intentos/control, GSI disperso, escrituras condicionales, creación de tablas en dev. | Pruebas con DynamoDB Local/LocalStack: reclamo concurrente, `ENTREGADO` inmutable, lease vencido |
| **7. RabbitMQ** | Topología declarativa, publisher con confirms, listeners con ack manual, colas de reintento (tiers), DLQ. | Prueba de integración: reintento por tiers, DLQ al agotar intentos |
| **8. Producer** | Cursor por `updated_at` con traslape, put condicional + publicación. | Pago que pasa a `PAGADO` tarde no se pierde; releer no duplica |
| **9. Consumer** | Máquina de estados completa (8.5), lookup tras `DESCONOCIDO`. | Escenarios: éxito, duplicado, 5xx, timeout, `LOST_ACK`, 4xx→DLQ |
| **10. Reconciliador** | Reencolado selectivo, barrido de consistencia, puesta al día al activar un destino, reencolado manual de DLQ. | **Fallo parcial:** banco OK + ERP caído + tercero OK ⇒ se reenvía **solo al ERP** |
| **11. Resiliencia** | Circuit breaker por destino, concurrencia por destino, apagado ordenado (graceful shutdown). | Destino caído no quema intentos ni frena a los otros |
| **12. Observabilidad** | Logs con `correlationId` (MDC), métricas Micrometer (entregas por destino/resultado, latencia, pendientes, DLQ), health (BD, RabbitMQ, DynamoDB). | Métricas visibles en `/actuator` |
| **13. Ejecución local** | `docker-compose.yml` (RabbitMQ con management, MariaDB con datos semilla, DynamoDB Local, mock de la tercera API), `.env.example`, perfil "destino caído". | `docker compose up` + arrancar el servicio ⇒ flujo completo funcionando |
| **14. Pruebas E2E** | Suite de escenarios con `X-Simulation-Scenario` (SUCCESS, SLOW, TIMEOUT, LOST_ACK, ERROR_500, ERROR_503, RATE_LIMIT) contra el mock y con WireMock. | Matriz de escenarios pasando |
| **15. Documentación y CI** | `README` (arquitectura, ejecución, variables, demo), ADRs breves (D2, D3, D6, retry tiers), runbook (DLQ, reconciliación), workflow de GitHub Actions (build + tests). | Un compañero puede levantar todo siguiendo el README |
| **16. Revisión final** | Checklist de la sección 13 y de entrega (sección 15). | Todo marcado |

### Matriz mínima de pruebas E2E
| Escenario | Resultado esperado |
|---|---|
| Todo OK en 3 destinos | 3 × `ENTREGADO`; comprobantes guardados; pago "completo" |
| Un destino con `ERROR_503` + `Retry-After` | Reintento respetando la espera; los otros dos no se tocan |
| `TIMEOUT` (no registra) | `DESCONOCIDO` → lookup `404` → reenvío → `ENTREGADO` |
| `LOST_ACK` (registra y tarda) | Timeout → lookup `200` o `409` duplicado → `ENTREGADO` sin duplicar |
| `422` / `400` | `DEAD_LETTER` inmediato, sin reintentos |
| Destino caído por completo | Breaker abierto, sin quemar intentos; al volver, se entrega |
| Caída del consumer a mitad | Redelivery + idempotencia ⇒ sin duplicados |
| Activar un destino nuevo | El reconciliador pone al día solo ese destino |
| Pago que se marca `PAGADO` tarde | Se detecta por `updated_at` y se envía |

---

## 13. Mejores prácticas (checklist de calidad exigido)

**Diseño**
- Arquitectura hexagonal (dominio sin dependencias de Spring/AWS/HTTP); inyección por constructor; clases pequeñas con una responsabilidad; objetos de valor inmutables (`record`).
- Sin lógica de negocio en listeners/schedulers: solo orquestan casos de uso.
- Configuración tipada y validada (`@ConfigurationProperties` + `@Validated`); fallar al arrancar si falta algo obligatorio.

**Corrección y robustez**
- Idempotencia de extremo a extremo (llave estable, escrituras condicionales, ack tras persistir).
- `BigDecimal` para dinero; zonas horarias explícitas; nunca `double`.
- Timeouts en **todas** las llamadas externas; sin reintentos ocultos de Feign; reintento gobernado por la cola con backoff y tope.
- Clasificación de errores por código HTTP/código de error; tolerar cuerpos fuera de contrato.
- Dead-letter explícito con causa registrada; nada se pierde en silencio.
- Apagado ordenado: terminar mensajes en vuelo, liberar leases.

**Seguridad**
- Secretos solo por variables de entorno; `.env.example` sin valores reales; `.gitignore` correcto.
- **No registrar** `x-api-key`, cuerpos completos ni datos personales. **No enviar el DPI.**
- Usuario de BD con solo `SELECT`; permisos IAM mínimos sobre las tres tablas.
- `server.error.include-stacktrace: never`; actuator limitado a endpoints necesarios.

**Observabilidad**
- `X-Correlation-Id` propagado productor → cola → consumer → API y presente en cada línea de log (MDC).
- Métricas y health checks; logs estructurados con niveles correctos.

**Pruebas**
- Pirámide: unitarias (mappers, clasificador, transiciones) → integración (Testcontainers) → E2E por escenarios.
- Pruebas deterministas (sin `sleep` arbitrarios: Awaitility).
- Cobertura alta en lógica de decisión; las pruebas documentan el comportamiento.

**Entrega**
- Commits pequeños y descriptivos (Conventional Commits); una rama por fase si se usa Git Flow; código generado fuera del control de versiones.
- README, ADRs y runbook; `docker compose` reproducible.
- Nada de TODOs sin dueño; nada de código muerto.

---

## 14. Reglas de trabajo para el chat que ejecute este plan

1. **Leer este documento y todos los archivos adjuntos antes de escribir código.** Responder primero con: (a) resumen de lo entendido en 10 líneas, (b) supuestos, (c) estado del entorno, (d) dudas bloqueantes (solo las realmente bloqueantes).
2. **Los YAML mandan.** No inventar campos ni comportamientos del contrato. Si este documento difiere de un YAML, usar el YAML y avisar.
3. Trabajar **fase por fase** (sección 12), mostrando al final de cada una: qué se hizo, qué se verificó **de verdad** y qué no.
4. **Verificar de verdad.** Compilar y correr pruebas siempre que el entorno lo permita. Si el entorno **no** puede descargar dependencias (Maven Central) o no tiene Maven/Docker, decirlo desde la fase 0, entregar el código con el mayor cuidado y pedirle al usuario que compile localmente y pegue los errores. **Nunca afirmar que algo fue probado si no lo fue.**
5. Entregar el proyecto como **archivos reales** (y un `.zip` del proyecto completo al terminar), no solo fragmentos en el chat.
6. No introducir tecnologías fuera de lo acordado (sin Lambdas, sin SQS, sin JPA para la BD de HidroGest, sin modificar su esquema).
7. Ante una decisión no cubierta aquí, elegir la opción más simple que cumpla las mejores prácticas, **documentarla en un ADR** y seguir; preguntar solo si bloquea.
8. Los textos para el usuario, en español, claros y sin jerga innecesaria.
9. Si el contrato real de la tercera API llega, tratarlo como un cambio **localizado** al adaptador `TERCERO`.

---

## 15. Checklist de entrega final

- [ ] `mvn clean verify` en verde (compilación + pruebas)
- [ ] DTO generados de los 3 YAML, **sin** interfaces de API generadas
- [ ] Flujo completo local con `docker compose` (RabbitMQ, MariaDB semilla, DynamoDB Local, mock del tercero)
- [ ] Fallo parcial demostrado: reenvío **solo** al destino faltante
- [ ] `LOST_ACK`, `TIMEOUT`, 5xx, 4xx cubiertos por pruebas automatizadas
- [ ] Sin secretos en el repo; `.env.example` presente
- [ ] README + ADRs + runbook + CI
- [ ] Pendientes D4–D7 documentados con su valor por defecto

---

## 16. Pendientes que solo el docente (o el usuario) puede resolver

1. Confirmar `teamCode` (prueba de la sección 4).
2. `apiId` y llave del **ERP** (¿mismo gateway?).
3. La **tercera API** real (contrato, llave, URL).
4. Quién crea las tablas de **DynamoDB** y con qué credenciales/región.
5. Si `X-Simulation-Scenario` estará habilitado el día de la demo.
6. Semántica exacta de `periodo_fiscal` del ERP si el YAML no la aclara.

---

## 17. Enlaces de referencia

- Repositorio HidroGest (esquema y migraciones): https://github.com/HydroGest-Team/HydroGest
- Generador de proyectos Spring: https://start.spring.io
- Swagger Editor (validar/navegar los YAML sin modificarlos): https://editor.swagger.io
- Spring Cloud OpenFeign: https://docs.spring.io/spring-cloud-openfeign/reference/
- OpenAPI Generator, generador `spring`: https://openapi-generator.tech/docs/generators/spring/
- Spring AMQP / RabbitMQ: https://docs.spring.io/spring-amqp/reference/
- AWS SDK for Java v2 (DynamoDB Enhanced Client): https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/
- Resilience4j: https://resilience4j.readme.io/
- Testcontainers: https://java.testcontainers.org/
- WireMock: https://wiremock.org/docs/

(Los enlaces de referencia son orientativos; verificar versiones vigentes antes de usarlos.)
