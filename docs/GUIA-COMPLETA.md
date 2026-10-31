# Guía completa · pagos-sender (Feature 3: envío de pagos)

Documento de estudio: qué se construyó, cómo funciona, dónde está cada cosa, por qué se hizo así y
qué responder ante las preguntas más probables. Actualizado al **2026-10-27**.

> Fuentes de verdad, en este orden: los **YAML de contrato** (`docs/*-api.yaml`), el código, y
> luego los documentos. Si algo de aquí contradice un YAML, gana el YAML.

---

## Índice

1. [La idea en 30 segundos](#1-la-idea-en-30-segundos)
2. [Tecnologías y por qué cada una](#2-tecnologías-y-por-qué-cada-una)
3. [Arquitectura](#3-arquitectura)
4. [Recorrido completo de un pago](#4-recorrido-completo-de-un-pago)
5. [Dónde está cada cosa](#5-dónde-está-cada-cosa)
6. [Lectura de HidroGest (MariaDB)](#6-lectura-de-hidrogest-mariadb)
7. [La llave de idempotencia](#7-la-llave-de-idempotencia)
8. [Mapeo a cada destino](#8-mapeo-a-cada-destino)
9. [Estados de una entrega y DynamoDB](#9-estados-de-una-entrega-y-dynamodb)
10. [RabbitMQ: colas, reintentos y dead-letter](#10-rabbitmq-colas-reintentos-y-dead-letter)
11. [Qué se hace con cada respuesta de las APIs](#11-qué-se-hace-con-cada-respuesta-de-las-apis)
12. [Resiliencia: qué pasa cuando algo falla](#12-resiliencia-qué-pasa-cuando-algo-falla)
13. [Configuración](#13-configuración)
14. [Seguridad](#14-seguridad)
15. [Observabilidad y operación](#15-observabilidad-y-operación)
16. [Cómo ejecutarlo](#16-cómo-ejecutarlo)
17. [Pruebas](#17-pruebas)
18. [Qué se verificó contra las APIs reales](#18-qué-se-verificó-contra-las-apis-reales)
19. [Decisiones de arquitectura (ADR)](#19-decisiones-de-arquitectura-adr)
20. [Pendientes y limitaciones conocidas](#20-pendientes-y-limitaciones-conocidas)
21. [Preguntas probables y cómo responderlas](#21-preguntas-probables-y-cómo-responderlas)
22. [Glosario](#22-glosario)

---

## 1. La idea en 30 segundos

La Oficina del Agua cobra pagos en **HidroGest** (Laravel + MariaDB). Cada pago cobrado debe
llegar a **3 sistemas externos**: el **Banco**, el **ERP** y una **tercera API**.

El problema de negocio: *en cualquier momento una de las 3 APIs puede fallar y no se sabe cuál*.
El sistema debe saber qué destinos ya recibieron cada pago y **reenviar solo a los que faltan**,
sin duplicar en los que ya lo tienen.

`pagos-sender` es un **microservicio Spring Boot independiente** que:

1. **Lee** (solo lectura) los pagos `PAGADO` de la base de HidroGest.
2. Crea en **DynamoDB** un registro de estado por cada combinación (pago, destino).
3. Publica **un mensaje por (pago, destino)** en **RabbitMQ**.
4. Un **consumer** toma cada mensaje, llama a la API con **Feign** y guarda el resultado en DynamoDB.
5. Si falla, el mensaje espera en una **cola de reintento** y se reenvía solo. Si falla para
   siempre, va a **dead-letter**.
6. Un **reconciliador** revisa periódicamente que nada se quede atascado o sin enviar.

---

## 2. Tecnologías y por qué cada una

| Tecnología | Versión | Para qué | Por qué |
|---|---|---|---|
| **Java (JDK)** | **17** | Lenguaje | Requisito del curso. `pom.xml` compila con `release 17`; la imagen Docker usa Temurin 17. |
| **Spring Boot** | **3.5.6** | Framework base | Requisito; la versión es la usada en clase. |
| **Spring Cloud OpenFeign** | 2025.0.0 | Clientes HTTP de las 3 APIs | Requisito ("usar FeignClient"). Interfaces escritas **a mano**. |
| **openapi-generator-maven-plugin** | 7.12.0 | Genera **solo los DTO** desde los YAML | Requisito: "no generar los specs/interfaces, solo DTO". |
| **RabbitMQ** | 3.13 | Cola productor/consumidor | La diapositiva de clase lo indica (no SQS). |
| **Spring AMQP** | (de Boot) | Cliente de RabbitMQ | Estándar en Spring. |
| **DynamoDB** (Local en Docker) | 2.5.2 | Estado de envío por destino + bitácora | Requisito: "DynamoDB guarda qué pagos se enviaron y cuáles no". Es **nuestro**, local. |
| **AWS SDK v2** | — | Cliente de DynamoDB | Oficial; permite escrituras condicionales. |
| **MariaDB + Spring JDBC** | 11.4 | Leer HidroGest | **Solo lectura**, sin JPA: no tocamos el esquema ajeno. |
| **Resilience4j** | — | Circuit breaker por destino | Que un destino caído no queme reintentos ni frene a los otros. |
| **Micrometer + Actuator** | — | Métricas y health | Observabilidad. |
| **Spring Security** | — | Proteger `/admin` y métricas | Basic Auth. |
| **Testcontainers, JUnit 5, AssertJ** | — | Pruebas | Integración con MariaDB y DynamoDB reales en contenedores. |
| **Docker Compose** | — | Entorno local completo | Que cualquiera lo levante con un comando. |

**Lo que NO se usa, a propósito:** AWS Lambda (el consumer escribe directo en DynamoDB), SQS (la
cola es RabbitMQ), JPA/Hibernate sobre HidroGest (solo `SELECT` con `JdbcTemplate`).

---

## 3. Arquitectura

```
 HidroGest (MariaDB) ── SOLO LECTURA (usuario con permiso SELECT)
        │
        │  PRODUCER (cada 10 s): pagos PAGADO con updated_at nuevo
        ▼
 DynamoDB pagos_entregas: crea PENDIENTE por (pago, destino) ── solo si no existía
        │
        │  publica 1 mensaje por (pago, destino), con publisher confirms
        ▼
 RabbitMQ  pagos.exchange ─► pagos.banco ─────┐
                          ─► pagos.erp ───────┤  CONSUMER (2 hilos por destino)
                          ─► pagos.tercero ───┘        │
        ▲                                              │ Feign (timeouts 2 s / 3 s)
        │ colas de espera con TTL                      ▼
        │ pagos.<d>.retry.{5s,30s,2m,10m}      Banco · ERP · Tercero
        │                                              │
        └──────── si falla y se puede reintentar ◄─────┤
                                                       │
 DynamoDB pagos_entregas (estado) + pagos_intentos (bitácora) ◄─┘
 pagos.<d>.dlq ◄── fallo definitivo (DEAD_LETTER)

 RECONCILIADOR (cada 1 min): reencola entregas vencidas/atascadas
 BARRIDO (cada 10 min): pagos PAGADO sin estado para algún destino → los crea y encola
```

**Clave del diseño:** un mensaje por **(pago, destino)**. Si el ERP está caído, solo los mensajes
del ERP esperan; banco y tercero siguen entregando. Así se cumple "reenviar solo a los que faltan".

**Estilo hexagonal (puertos y adaptadores):**

- `domain/`: modelos y **puertos** (interfaces). No depende de Spring, AWS ni HTTP.
- `application/`: casos de uso (producer, consumer, reconciliador, reglas de decisión).
- `infrastructure/`: adaptadores concretos (MariaDB, DynamoDB, RabbitMQ, Feign, seguridad).

Ventaja: agregar o cambiar un destino (por ejemplo, cuando llegue la tercera API real) solo toca su
adaptador.

---

## 4. Recorrido completo de un pago

Ejemplo: la cajera cobra el pago **900010** por Q120.50 en efectivo.

1. **HidroGest** guarda el pago con `estado_pago = 'PAGADO'` y actualiza `updated_at`.
2. **Producer** (`PaymentDispatcher`, cada 10 s): consulta los pagos `PAGADO` con marca de tiempo
   ≥ cursor − 10 min. Encuentra el 900010.
3. **Enqueuer** (`DeliveryEnqueuer`): para BANCO, ERP y TERCERO intenta crear en DynamoDB el
   registro `PENDIENTE` con `PutItem` condicional (`attribute_not_exists`). Solo si **lo creó**,
   publica el mensaje `{paymentId: 900010, destino: BANCO, attempt: 0, correlationId: <uuid>}`.
   Releer el mismo pago no duplica nada.
4. **RabbitMQ** confirma la recepción (*publisher confirm*). El producer avanza el cursor.
5. **Consumer** (`DeliveryListener` → `DeliveryProcessor`) toma el mensaje de `pagos.banco`:
   1. Lee el estado. Si ya está `ENTREGADO` o `DEAD_LETTER`, hace *ack* y termina.
   2. **Reclama** el envío: escritura condicional a `EN_VUELO` con un *lease* de 30 s. Si otro
      consumer lo tiene, descarta el mensaje.
   3. Vuelve a leer el pago de MariaDB (el mensaje no lleva datos personales) y lo **valida**
      localmente (monto, fecha, formato de llave…).
   4. Si el intento anterior quedó **incierto** (timeout), primero **consulta** al destino con `GET`.
   5. **Envía** con Feign: `POST /bank/v1/payments` con header `Idempotency-Key: EQA-900010`.
   6. Clasifica la respuesta (sección 11), registra el intento en `pagos_intentos` y actualiza
      `pagos_entregas`.
   7. Hace *ack* del mensaje **después** de guardar el estado.
6. Si el banco respondió `201`: estado `ENTREGADO` con `receiptId = BNK-…`. Cuando los 3 destinos
   están `ENTREGADO`, el log dice **"Pago completo"**.
7. Si el ERP respondió `503`: estado `REINTENTO`, el mensaje se publica en `pagos.erp.retry.5s`.
   A los 5 s RabbitMQ lo devuelve a `pagos.erp` y se intenta de nuevo, con la **misma llave**.

---

## 5. Dónde está cada cosa

Paquete base: `src/main/java/gt/edu/umg/desarrolloweb/pagos/`

### Dominio (`domain/`)
| Archivo | Qué es |
|---|---|
| `Payment.java` | Instantánea inmutable del pago (sin DPI). Convierte a centavos y a texto con 2 decimales. |
| `Destino.java` | `BANCO`, `ERP`, `TERCERO`. |
| `EstadoEntrega.java` | `PENDIENTE`, `EN_VUELO`, `REINTENTO`, `DESCONOCIDO`, `ENTREGADO`, `DEAD_LETTER`. |
| `Outcome.java` | Resultado de una llamada: `DELIVERED`, `RETRY`, `UNKNOWN`, `DEAD_LETTER`. |
| `DeliveryResult.java`, `LookupResult.java` | Resultado de un envío / de una consulta. |
| `PaymentValidator.java` | Validaciones previas al envío (reglas de los YAML). |
| Puertos | `PaymentSource`, `DeliveryStateStore`, `AttemptLog`, `CursorStore`, `DeliveryPublisher`, `DestinationGateway`. |

### Aplicación (`application/`)
| Archivo | Qué es |
|---|---|
| `PaymentDispatcher.java` | **Producer**: cursor por `updated_at` con traslape. |
| `DeliveryEnqueuer.java` | Crea `PENDIENTE` condicional y publica. |
| `DeliveryProcessor.java` | **Consumer**: la máquina de estados completa. |
| `ReconciliationService.java` | Reencola vencidos y hace el barrido de consistencia. |
| `ResponseClassifier.java` | La **tabla de decisión** por código HTTP y código de error. |
| `RetryPolicy.java` | Elige la espera (tier) y decide cuándo se agotaron los intentos. |
| `IdempotencyKeys.java` | Genera la llave `EQA-900010`. |
| `DeliveryAdminService.java` | Estado de un pago y reencolado manual de dead-letter. |
| `DeliveryMetrics.java` | Métricas de negocio. |
| `PagosProperties.java` | Configuración tipada y validada (`pagos.*`). |

### Infraestructura (`infrastructure/`)
| Carpeta | Contenido |
|---|---|
| `mariadb/MariaDbPaymentSource.java` | Las consultas `SELECT` a HidroGest. |
| `dynamodb/` | Adaptadores de estado (`DynamoDbDeliveryStateStore`), bitácora (`DynamoDbAttemptLog`), cursor (`DynamoDbCursorStore`) y creación de tablas (`DynamoDbTables`). |
| `rabbit/` | Topología (`RabbitTopology`), publisher con confirms (`RabbitDeliveryPublisher`), listeners (`DeliveryListener`). |
| `destinos/banco`, `destinos/erp`, `destinos/tercero` | Por destino: `*FeignClient` (a mano), `*FeignConfig`, `*Mapper`, `*Gateway`. |
| `destinos/common/` | Base de los gateways (`HttpDestinationGateway`), decodificador de errores tolerante (`ContractErrorDecoder`), interceptores y timeouts (`FeignDestinoSupport`), `Montos`. |
| `scheduling/PagosSchedulers.java` | Los 3 disparadores periódicos (solo orquestan). |
| `admin/AdminController.java` | Endpoints `/admin`. |
| `config/` | Seguridad, health de destinos, beans comunes. |

### Otros archivos importantes
| Ruta | Qué es |
|---|---|
| `pom.xml` | Dependencias y las 3 ejecuciones del generador de DTO. |
| `src/main/resources/application.yml` | Toda la configuración (por variables de entorno). |
| `src/main/resources/openapi/*.yaml` | Copias **idénticas** de los contratos (el CI lo verifica con `cmp`). |
| `target/generated-sources/openapi/{banco,erp,tercero}` | DTO generados. **No se editan ni se versionan.** |
| `docker-compose.yml`, `Dockerfile` | Entorno local y la imagen. |
| `docker/mariadb/` | Esquema de HidroGest, datos semilla, usuario de solo lectura, rutinas para simular a la cajera, scripts `nuevo-cobro.sh` y `simular-cobro.sh`. |
| `docker/third-mock/mock_third_api.py` | Mock de la tercera API. |
| `docs/adr/` | Decisiones de arquitectura (0001–0007). |
| `docs/RUNBOOK.md` | Operación: dead-letter, reencolar, forzar fallos, empezar de cero. |
| `docs/PRUEBAS-GRAFICAS.md`, `docs/postman/` | Pruebas con Adminer, DynamoDB Admin, RabbitMQ y Postman. |
| `.github/workflows/ci.yml` | CI: verifica contratos sin cambios + `mvn clean verify` con JDK 17. |

---

## 6. Lectura de HidroGest (MariaDB)

- **Solo `SELECT`**, por tres capas: el usuario de BD solo tiene `GRANT SELECT`
  (`docker/mariadb/03-usuario-solo-lectura.sh`), el pool está en `read-only: true` y el código
  solo usa consultas `SELECT`.
- Camino de datos: `tb_pagos → tb_lecturas → tb_contadores → tb_clientes` y `tb_lecturas → tb_periodos`.
- **No se selecciona el DPI** (`dpi_cliente`). Nunca sale del sistema.
- **Elegible** = `estado_pago = 'PAGADO'`.
- **Marca de tiempo** = `COALESCE(updated_at, created_at, fecha_pago)`. Laravel puede dejar
  `updated_at` en NULL si la fila se insertó fuera de Eloquent.

**¿Por qué cursor por `updated_at` y no por `id`?** Un pago nace `NO PAGADO` y puede pasar a
`PAGADO` días después. Con un cursor por `id` se perdería, porque su `id` ya quedó atrás. Con
`updated_at`, cuando se cobra su marca de tiempo avanza y el producer lo ve. Además se relee un
**traslape de 10 min** para tolerar transacciones que confirman tarde. Releer es inofensivo
gracias al `PutItem` condicional.

**Fechas:** `fecha_pago` es `DATETIME` sin zona y se interpreta en **America/Guatemala** (UTC-6).
Se envía como ISO-8601 con offset, por ejemplo `2026-10-27T16:39:47-06:00`.

**La réplica local** (`docker/mariadb/01-esquema-hidrogest.sql`) es una copia del esquema de las
migraciones de Laravel. Los datos semilla son los pagos 900001–900006, con fechas fijas. Los
cobros simulados (`simular_nuevo_cobro`) usan ids de un **rango propio por instalación**
(`segundos unix al crear el volumen × 1000`, ej. `1793143394000`), para que sus llaves no choquen
en los simuladores compartidos al recrear la réplica ni entre compañeros (ADR-0007).

---

## 7. La llave de idempotencia

Formato: `<prefijo><id con 6 dígitos>`. El prefijo se deriva de `TEAM_CODE`
(`EQUIPO_A → EQA-`). Ejemplos: `EQA-000123`, `EQA-900010`.

- **Es la misma en todos los reintentos y en los 3 destinos.** Nunca un UUID por intento.
- Banco: header `Idempotency-Key`. ERP: `documento_origen.numero` en el body. Tercero: header
  `Idempotency-Key`.
- Gracias a ella, si reenviamos algo que el destino ya tenía, responde **409 duplicado** en vez de
  registrarlo dos veces. Eso permite reintentar sin miedo.

---

## 8. Mapeo a cada destino

| Campo | Banco | ERP | Tercero (provisional) |
|---|---|---|---|
| Unicidad | header `Idempotency-Key` | `documento_origen.numero` | header `Idempotency-Key` |
| Equipo | `teamCode` | `equipo` | `teamCode` |
| Cliente | `payer.customerCode` = `CLI-0001` | `contribuyente.codigo_cliente` | `customer.code` |
| Nombre | `payer.fullName` (≤150) | — | `customer.name` |
| NIT | `payer.taxId` = `CF` | `contribuyente.nit` = `CF` | — |
| Contador | — | `contribuyente.codigo_contador` | — |
| Monto | `amount.valueInCents` (entero, ej. 12050) | `total` = `"120.50"` + 1 línea de detalle | `amount` = `"120.50"` |
| Método | Efectivo→`CASH`, Crédito/Débito→`CARD` | `EFECTIVO` / `TARJETA` | `CASH` / `CARD` |
| Tipo de documento | — | `RECIBO_CAJA` / `VOUCHER_TARJETA` | — |
| Período | `description` = "Servicio de agua 2026-09" | `periodo_fiscal{anio, mes}` | `period` = `2026-09` |
| Recibo | `metadata.numeroRecibo` | `observaciones` = "Recibo …" | `reference` |

- **Dinero siempre `BigDecimal`**, nunca `double`. A centavos:
  `setScale(2, UNNECESSARY).movePointRight(2).longValueExact()`. Si el monto tuviera más de 2
  decimales, falla en vez de redondear.
- **ERP:** una sola línea `AGUA_POTABLE` con `monto = total`, así la suma del detalle **siempre
  cuadra** (regla `TOTAL_NO_CUADRA`).
- **Período:** el período facturado de la lectura (`tb_periodos.fecha_apertura`), según el ejemplo
  del YAML del ERP (ADR-0005).

**Validaciones previas** (`PaymentValidator`), para no gastar una llamada en algo que el destino
rechazaría: pago `PAGADO`, llave con formato `^[A-Za-z0-9_-]{8,64}$`, monto > 0 y con 2 decimales
como máximo, fecha no más de 5 min en el futuro, banco ≤ Q25,000.00, tercero ≤ 25000.00 y recibo
≤ 30 caracteres, llave del ERP ≤ 40. Si falla → `DEAD_LETTER` con código `VALIDACION_LOCAL`.

---

## 9. Estados de una entrega y DynamoDB

### Máquina de estados

```
PENDIENTE ──reclamo──► EN_VUELO ──201 / 409 duplicado / GET encontrado──► ENTREGADO  (final)
                          │
                          ├──429/500/502/503, sin conexión──► REINTENTO ──(espera)──► EN_VUELO
                          ├──timeout de lectura / 504────────► DESCONOCIDO ─(espera)─► EN_VUELO → primero GET
                          └──400/403/422/409 reutilizado,
                             intentos agotados, validación──► DEAD_LETTER (final; solo reencolado manual)
```

| Estado | Significado |
|---|---|
| `PENDIENTE` | Creado, aún no procesado. |
| `EN_VUELO` | Un consumer lo está enviando (tiene *lease* de 30 s). |
| `REINTENTO` | Falló de forma transitoria; espera en una cola TTL. |
| `DESCONOCIDO` | No sabemos si el destino lo registró (timeout). El próximo intento **consulta primero**. |
| `ENTREGADO` | Tiene comprobante. **Nunca se sobrescribe.** |
| `DEAD_LETTER` | Fallo definitivo. No se reintenta solo. |

### Tablas (DynamoDB Local, nuestro, en Docker)

| Tabla | Clave | Para qué |
|---|---|---|
| `pagos_entregas` | PK `paymentId` (N), SK `destino` (S) | Estado actual por (pago, destino): `estado`, `intentos`, `receiptId`, `idempotencyKey`, `lastHttpStatus`, `lastErrorCode`, `nextAttemptAt`, `leaseUntil`, `version`, `openKey`… |
| `pagos_intentos` | PK `paymentId#DESTINO`, SK `epochMillis#intento` | **Bitácora**: una fila por cada llamada (`SEND`/`LOOKUP`, resultado, HTTP, código, duración, correlationId). TTL de 90 días. Es el "DynamoDB Logs" del diagrama. |
| `pagos_control` | PK `pk` | El cursor del producer. |

- **GSI disperso `abiertos-index`** (`openKey`, `nextAttemptAt`): `openKey = "OPEN"` existe solo
  mientras la entrega **no** es terminal. Al pasar a `ENTREGADO`/`DEAD_LETTER` se borra el
  atributo y la fila sale del índice. Así el reconciliador encuentra lo pendiente **sin escanear**
  toda la tabla.
- **Escrituras condicionales:** crear (`attribute_not_exists`), reclamar (`estado IN (PENDIENTE,
  REINTENTO, DESCONOCIDO)` o `EN_VUELO` con lease vencido) y cerrar (por `version`). Esto evita
  que dos consumers envíen lo mismo y que se pise un `ENTREGADO`.
- Las tablas se crean solas al arrancar porque `DYNAMO_CREAR_TABLAS=true` en compose.
- Para verlas: **DynamoDB Admin** en http://localhost:8001.

---

## 10. RabbitMQ: colas, reintentos y dead-letter

Por **cada destino** `<d>` (`banco`, `erp`, `tercero`):

| Cola | Tipo | Para qué |
|---|---|---|
| `pagos.<d>` | quorum, durable | Cola principal; la consume el listener del destino. |
| `pagos.<d>.retry.5s` / `.30s` / `.2m` / `.10m` | durable con TTL | Espera. Al vencer el TTL el mensaje **vuelve solo** a `pagos.<d>`. Nadie las consume. |
| `pagos.<d>.dlq` | quorum | Dead-letter (informativa; el estado que manda es el de DynamoDB). |

Exchanges: `pagos.exchange` (direct) y `pagos.dlx` (dead-letter).

**Reintento no bloqueante:** ante un fallo, el consumer guarda el estado, publica el mensaje en la
cola de espera adecuada y hace *ack* del original. No hay un hilo dormido esperando. Además
**sobrevive a reinicios**: el mensaje queda persistido en RabbitMQ (verificado el 27-oct).

**Política (`RetryPolicy`):**
- Espera según el número de intento: 1.º → 5 s, 2.º → 30 s, 3.º → 2 min, 4.º y siguientes → 10 min.
- Nunca menos que el `Retry-After` del destino (en `429`/`503`); se elige el tier más corto ≥ esa
  espera.
- **Máximo 6 intentos** (`MAX_INTENTOS`). Al 6.º fallo → `DEAD_LETTER` + DLQ. En total son
  unos 23 minutos de reintentos.

**Garantías de entrega:**
- *Publisher confirms* + `mandatory`: el producer espera la confirmación del broker; si un
  mensaje no se puede enrutar, falla en vez de perderse.
- **Ack manual**, siempre **después** de guardar en DynamoDB.
- `prefetch: 2` y 2 consumidores por destino (configurable). Un listener por destino funciona como
  *bulkhead*: un destino lento no frena a los otros.
- `x-delivery-limit: 10` en la cola principal: un mensaje que falle 10 veces sin *ack* (por ejemplo,
  un cuerpo ilegible) va a la DLQ.
- Sin reintentos ocultos: Feign con `Retryer.NEVER_RETRY` y `listener.retry.enabled: false`.
  Todo reintento pasa por la cola y queda registrado.

---

## 11. Qué se hace con cada respuesta de las APIs

Se decide por **código HTTP y código de error** del contrato, **nunca** por el texto del mensaje.
Implementado en `ResponseClassifier` y `HttpDestinationGateway`.

| Respuesta | Resultado | Acción |
|---|---|---|
| `201` | ENTREGADO | Guarda el comprobante (`BNK-…`, `ERP-…`, `THR-…`). |
| `409 DUPLICATE_PAYMENT` / `DOCUMENTO_DUPLICADO` **con el mismo monto** | ENTREGADO | Ya lo tenía: toma el comprobante original. |
| `409 …` duplicado **con otro monto** | DEAD_LETTER `LLAVE_REUTILIZADA` | La llave es de otro pago (ADR-0007, cambio del 27-oct). |
| `409 IDEMPOTENCY_KEY_REUSED` / `DOCUMENTO_REUTILIZADO` | DEAD_LETTER | Misma llave con otros datos: error nuestro. |
| `400`, `403`, `404`, `422` | DEAD_LETTER | Validación, llave/equipo incorrecto, regla de negocio. Reintentar no lo arregla. |
| `429`, `500`, `502`, `503` | REINTENTO | Respeta `Retry-After`. |
| `504`, timeout de lectura, red cortada | DESCONOCIDO | Pudo registrarse: el próximo intento hace `GET` primero. |
| Sin conexión / timeout de conexión | REINTENTO | El request ni salió. |
| `GET` → `200` con el mismo monto | ENTREGADO | Sin reenviar. |
| `GET` → `404` | — | No existe: se envía el `POST` con la misma llave. |

**Cuerpos fuera de contrato:** API Gateway responde `{"message":"Forbidden"}` (o HTML en un 502)
sin los campos del contrato. El `ContractErrorDecoder` lo tolera: si no encuentra el código, decide
solo por el estado HTTP.

---

## 12. Resiliencia: qué pasa cuando algo falla

| Situación | Qué hace el sistema |
|---|---|
| **Un destino responde 5xx** | `REINTENTO` en ese destino; los otros dos siguen normales. |
| **Un destino tarda más de 3 s** | `DESCONOCIDO` → en el siguiente intento consulta con `GET`: si está, `ENTREGADO` sin duplicar; si no, reenvía. |
| **`LOST_ACK`** (registra pero la respuesta no llega) | Igual que arriba: el `GET` lo encuentra, o el reenvío recibe `409 duplicado` → `ENTREGADO`. |
| **Un destino está caído por completo** | El **circuit breaker** de ese destino se abre (≥50 % de fallos en las últimas 10 llamadas, mínimo 5). Mientras está abierto **no se llama** y los mensajes esperan **sin gastar intentos**. A los 30 s prueba con 2 llamadas (*half-open*). |
| **El consumer muere a mitad de un envío** | El mensaje no tuvo *ack* y RabbitMQ lo reentrega. Si el estado quedó `EN_VUELO`, al vencer el lease (30 s) se retoma y **consulta primero**. |
| **El proceso cae entre crear el estado y publicar** | El reconciliador (cada 1 min) ve el `PENDIENTE` vencido en el GSI y lo reencola. |
| **Se activa un destino nuevo** | El barrido (cada 10 min) crea y encola solo lo que le falta a ese destino. Con `pagos.desde.<DESTINO>` se limita a pagos desde una fecha. |
| **Falla DynamoDB / MariaDB / RabbitMQ** | El listener reprograma el mensaje con espera ≥ lease; si ni eso puede, lo devuelve a la cola (`nack`). El readiness pasa a DOWN. |
| **Apagado del servicio** | `server.shutdown: graceful` + 30 s por fase: termina los mensajes en vuelo. |

**Timeouts:** connect 2 s y read 3 s en los 3 destinos (lo que recomiendan los YAML). DynamoDB:
2 s por intento, 5 s en total. MariaDB: 5 s para obtener conexión.

---

## 13. Configuración

Todo se configura por **variables de entorno**. En local salen de `.env`, que está en
`.gitignore`. La plantilla, con placeholders, es `.env.example`.

### Variables imprescindibles
| Variable | Qué es |
|---|---|
| `TEAM_CODE` | `EQUIPO_A`. Debe corresponder a la API key; de aquí sale el prefijo `EQA-`. |
| `BANK_BASE_URL`, `BANK_API_KEY` | Simulador del banco (AWS del docente). |
| `ERP_BASE_URL`, `ERP_API_KEY` | Simulador del ERP (mismo API Gateway). |
| `THIRD_BASE_URL`, `THIRD_API_KEY` | Tercera API. En compose apunta al mock. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | HidroGest; el usuario es de **solo lectura**. |
| `RABBIT_HOST`, `RABBIT_USER`, `RABBIT_PASSWORD` | RabbitMQ. |
| `DYNAMO_ENDPOINT` | Con valor = DynamoDB Local. Vacío = AWS real. |
| `ADMIN_USER`, `ADMIN_PASSWORD` | Basic Auth de `/admin` y métricas. Vacío = cerrados. |
| `MARIADB_ROOT_PASSWORD`, `HIDROGEST_APP_PASSWORD` | Solo para la réplica local en compose. |

### Variables de ajuste (tienen valor por defecto)
| Variable | Default | Efecto |
|---|---|---|
| `DESTINOS_ACTIVOS` | `BANCO,ERP,TERCERO` | Quitar uno lo desactiva sin tocar código. |
| `BANK_SIM_SCENARIO`, `ERP_SIM_SCENARIO`, `THIRD_SIM_SCENARIO` | vacío | Envía `X-Simulation-Scenario` (`SUCCESS`, `SLOW`, `TIMEOUT`, `LOST_ACK`, `ERROR_500`, `ERROR_503`, `RATE_LIMIT`). **Solo pruebas.** |
| `PRODUCER_INTERVALO` / `PRODUCER_LOTE` / `PRODUCER_TRASLAPE` | 10s / 200 / 10m | Producer. |
| `MAX_INTENTOS` / `RETRY_TIERS` | 6 / `5s,30s,2m,10m` | Reintentos. |
| `RECONCILIADOR_INTERVALO` / `_LEASE` / `_GRACIA` / `_BARRIDO` | 1m / 30s / 1m / 10m | Reconciliador. |
| `CONSUMER_CONCURRENCIA_BANCO` / `_ERP` / `_TERCERO` | 2 | Hilos por destino. |
| `IDEMPOTENCY_KEY_PREFIX` | vacío (derivado) | Prefijo distinto de llave. |
| `ZONA_FECHA_PAGO` | `America/Guatemala` | Zona de `fecha_pago`. |

### Detalles de configuración que conviene saber
- `docker-compose.yml` **sobrescribe** los hosts dentro de la red Docker (`mariadb`, `rabbitmq`,
  `dynamodb-local:8000`, `third-api-mock:8089`) y activa la creación de tablas.
- Los `*FeignConfig` **no** llevan `@Configuration` a propósito: si lo tuvieran, el interceptor con
  la API key del banco se aplicaría también a los otros clientes.
- `spring.jackson.default-property-inclusion: non_null`: los DTO generados no mandan `null`.
- `pom.xml` no hereda de `spring-boot-starter-parent`: las versiones de plugins están fijadas a
  mano y el compilador usa `-parameters` (sin eso fallan `@PathVariable`).
- El BOM de Testcontainers va **antes** que el de Spring Boot (Docker Engine 29 exige API ≥ 1.44).
- Generación de DTO: 3 ejecuciones del plugin con `generateApis=false`, `generateModels=true`,
  `generateSupportingFiles=false`, sufijo `Dto`. Si algo no sirve se cambia **la configuración del
  plugin**, nunca el YAML ni el código generado.

### Puertos locales
| Servicio | URL |
|---|---|
| pagos-sender | http://localhost:8080 |
| RabbitMQ (management) | http://localhost:15672 (AMQP en 5672) |
| MariaDB | localhost:3306 |
| DynamoDB Local | http://localhost:8000 |
| Mock tercera API | http://localhost:8089 |
| Adminer (ver/editar HidroGest) | http://localhost:8081 |
| DynamoDB Admin (ver estados) | http://localhost:8001 |

---

## 14. Seguridad

- **Secretos solo en `.env`** (ignorado por Git). Nunca en código, `application.yml`, compose,
  pruebas ni logs.
- **No se registra** la `x-api-key`, ni cuerpos completos, ni datos personales. El log de Feign
  está en nivel `BASIC`: método, URL, estado y duración.
- **No se envía el DPI** a ningún destino (ni siquiera se lee).
- BD de HidroGest: usuario con **solo SELECT** + pool `read-only`.
- `/actuator/health` es público, sin detalles. `/admin/**` y el resto de `/actuator/**` requieren
  Basic Auth con rol ADMIN. Todo lo demás se rechaza (`denyAll`).
- `server.error.include-stacktrace: never` e `include-message: never`.
- La imagen corre con un usuario sin privilegios (`pagos`), sobre JRE Alpine.
- `X-Simulation-Scenario` está apagado por defecto: solo se envía si se configura.

---

## 15. Observabilidad y operación

**Logs:** cada línea lleva `[corr=<correlationId>] [pago=<id> <DESTINO>]` (MDC). El
`X-Correlation-Id` viaja producer → mensaje → consumer → header HTTP hacia la API, así se puede
seguir un pago de punta a punta, incluso del lado del docente.

```bash
docker compose logs -f pagos-sender | grep 'pago=900010'
```

**Métricas** (`/actuator/prometheus`, con Basic Auth):
- `pagos_intentos_total{destino,operacion,resultado}` y `pagos_intentos_latencia_seconds`
- `pagos_entregas_cerradas_total{destino,estado}`
- `pagos_completos_total`: pagos entregados a todos los destinos activos
- `pagos_entregas_abiertas{destino}`: pendientes, en vuelo o en reintento
- `pagos_dlq_mensajes{destino}`

**Health:** `readiness` depende solo de BD, RabbitMQ y DynamoDB. Que un destino externo caiga no
saca de servicio al proceso; eso se ve aparte en el grupo `destinos`, con el estado del breaker.

**Endpoints de administración** (Basic Auth):
| Método y ruta | Qué hace |
|---|---|
| `GET /admin/pagos/{id}` | Estado por destino, comprobantes y bitácora de intentos; `completo: true/false`. |
| `POST /admin/pagos/{id}/destinos/{DESTINO}/reencolar` | Saca un `DEAD_LETTER`, lo pone `PENDIENTE` con intentos en 0 y lo reenvía con la **misma llave**. |
| `POST /admin/producer` | Ejecuta el producer ya. |
| `POST /admin/reconciliar` | Ejecuta el reconciliador y el barrido ya. |

Procedimientos detallados: `docs/RUNBOOK.md`.

---

## 16. Cómo ejecutarlo

### En esta máquina
- Maven no está en el PATH. Está en
  `C:\Users\PREDATOR\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin`.
- El JDK instalado es el 25. Compila igual para Java 17 por `release 17`, y la imagen Docker usa JDK 17.
- Docker Desktop (con WSL2) hay que **iniciarlo** antes.

### Comandos
```bash
mvn clean verify                 # compila, genera DTO, 69 unitarias + 11 de integración
docker compose up -d             # levanta todo (RabbitMQ, MariaDB, DynamoDB, mock, Adminer, DynamoDB Admin, servicio)
docker compose ps                # todo "healthy"
docker compose logs -f pagos-sender
docker compose down              # apagar (SIN -v: conserva los datos)
```

### Simular a la cajera
```bash
docker/mariadb/nuevo-cobro.sh 120.50 Efectivo            # pago nuevo PAGADO → se envía en ~10 s
docker/mariadb/nuevo-cobro.sh 75.00 Debito pendiente     # NO PAGADO
docker/mariadb/simular-cobro.sh <id>                     # cobrarlo después
```
También desde Adminer: Rutinas → `simular_nuevo_cobro` / `simular_cobro_tardio`. Escriben con el
usuario de "Laravel", no con el de este servicio.

### Forzar fallos para una demo
```bash
ERP_SIM_SCENARIO=ERROR_503 docker compose up -d pagos-sender   # ERP falla; banco y tercero siguen
docker compose up -d pagos-sender                              # quitar el fallo: lo pendiente se entrega solo
docker compose stop third-api-mock                             # destino caído: el breaker se abre
docker compose start third-api-mock                            # al volver, se entrega
```

---

## 17. Pruebas

| Tipo | Cantidad | Qué cubren |
|---|---|---|
| Unitarias (`*Test`, surefire) | **69** | Tabla de decisión (todos los códigos), política de reintentos y llaves, validaciones, máquina de estados del consumer (éxito, duplicado, 503 con Retry-After, LOST_ACK, timeout→404→reenvío, lease vencido, fallo parcial, circuito abierto), mappers contra los contratos, decodificador de errores con cuerpos de API Gateway, errores de red, llave reutilizada. |
| Integración (`*IT`, failsafe) | **11** | Con **Testcontainers**: MariaDB con el esquema real de HidroGest (consultas, pago tardío, sin DPI) y DynamoDB Local (reclamo concurrente, `ENTREGADO` inmutable, lease vencido, GSI). |
| Manuales E2E | — | Contra los simuladores reales y el mock (sección 18), con Postman y las herramientas gráficas. |

```bash
mvn test -Dtest=ResponseClassifierTest                           # una clase
mvn -Dit.test=MariaDbPaymentSourceIT failsafe:integration-test   # una de integración
```

---

## 18. Qué se verificó contra las APIs reales

Verificado el **2026-10-27**, ejecutado y con la salida revisada:

| Prueba | Resultado |
|---|---|
| `mvn clean verify` | ✅ BUILD SUCCESS (69 + 11 pruebas). |
| `docker compose up -d` + `ps` | ✅ Todos los servicios healthy. |
| Pagos semilla 900001–900005 × 3 destinos | ✅ 15 entregas `ENTREGADO` con comprobante. |
| Timeout real del banco | ✅ `DESCONOCIDO` → `GET` 200 → `ENTREGADO` sin reenviar. |
| `500` real del ERP | ✅ `REINTENTO` → reenvío a los 5 s → entregado. |
| ERP forzado a `ERROR_503` | ✅ Reintentos a 5 s, 30 s y 2 min. Al quitar el fallo y **reiniciar el servicio**, el mensaje que esperaba en `pagos.erp.retry.2m` se entregó solo. Banco y tercero no se tocaron. |
| Llave reutilizada con otro monto (pago 900008, Q99.99 frente a Q47.80 en el simulador) | ✅ Banco y ERP en `DEAD_LETTER LLAVE_REUTILIZADA`; tercero `ENTREGADO`. Además el ERP real dio un `503` en medio y el reintento automático funcionó. |

### El problema que se encontró y se corrigió (ADR-0007)
La réplica local se había recreado y volvió a generar ids que ya se habían usado en pruebas
anteriores (`EQA-900007` estaba en el banco con Q135.25 desde el 23-oct). Al enviar un pago
**distinto** (Q142.35) con esa llave, los simuladores respondieron `409 DUPLICATE_PAYMENT` en vez de
`IDEMPOTENCY_KEY_REUSED`, y el servicio lo daba por entregado.

**Corrección (2 partes):**
1. **Detección en el servicio:** ante un duplicado o un `GET` exitoso se compara el monto
   registrado por el destino con el de HidroGest. Si difiere → `DEAD_LETTER LLAVE_REUTILIZADA`.
2. **Prevención en la réplica:** los cobros simulados toman ids de un rango propio por
   instalación, así no repiten llaves. Verificado: el pago `1793143394000` recibió `201` del banco
   y comprobante nuevo del ERP.

Detalle de la parte 1: Cambios en
`HttpDestinationGateway`, `ContractErrorDecoder`, `DestinationHttpException`, `Montos` y los tres
gateways, más 6 pruebas nuevas.

**Pendiente operativo:** cuando el ingeniero limpie los simuladores, hacer `docker compose down -v`
y `up -d` para que el estado local coincida (RUNBOOK §7). **Antes** de esa limpieza no usar `-v`.

---

## 19. Decisiones de arquitectura (ADR)

| ADR | Decisión | Por qué |
|---|---|---|
| 0001 | RabbitMQ, **sin Lambdas** | La diapositiva lo pide; el consumer puede escribir directo en DynamoDB, una Lambda solo añadiría piezas. |
| 0002 | Estado en DynamoDB; HidroGest **solo lectura** | HidroGest no tiene columna "enviado" y no podemos modificar su esquema. |
| 0003 | Reintentos con **colas TTL** + circuit breaker | No bloquean hilos, sobreviven a reinicios y un destino caído no quema intentos. |
| 0004 | Timeout = **DESCONOCIDO**: consultar antes de reenviar | Con `LOST_ACK` el destino sí registró; reenviar a ciegas solo es seguro gracias a la llave. |
| 0005 | `periodo_fiscal` = período facturado de la lectura | Así lo muestra el ejemplo del YAML del ERP. |
| 0006 | Tercera API **provisional** + mock | No fue entregada; cuando llegue, solo cambia `destinos/tercero`. |
| 0007 | Duplicado solo es "entregado" si **coincide el monto** | Caso real del 27-oct con llaves reutilizadas. |

---

## 20. Pendientes y limitaciones conocidas

**Pendientes externos (los decide el docente):**
- **D4:** quién crea las tablas DynamoDB en AWS. Hoy se usa **DynamoDB Local nuestro** y está bien así.
- **D5:** `teamCode` = `EQUIPO_A`. En la práctica está confirmado: banco y ERP tienen registrados
  pagos nuestros con comprobante (`BNK-…`, `ERP-…`) y nunca respondieron `TEAM_MISMATCH`.
- **D7:** el ERP usa el mismo API Gateway que el banco y funciona.
- **Tercera API real:** sigue siendo el mock provisional.
- Si el header `X-Simulation-Scenario` estará habilitado el día de la demo. Si no, usar el
  "destino caído" (`docker compose stop third-api-mock`).

**Limitaciones honestas:**
- La comparación anti-reutilización usa **solo el monto**, porque es el único dato del pago que
  devuelven los tres comprobantes. Una llave reutilizada **con el mismo monto** pasaría como
  entregada. Con la BD real de HidroGest los ids no se repiten, así que solo afecta a réplicas de prueba.
- Las llaves repetidas entre réplicas de prueba ya se previenen: los cobros simulados usan un rango
  de ids propio por instalación. Solo chocarían dos réplicas inicializadas en el mismo segundo.
- El banco real tarda 1–3 s y el timeout es 3 s (lo recomendado por el contrato), así que hay
  timeouts frecuentes. Se resuelven bien con la consulta posterior, pero generan reintentos.
- El servicio lee la **réplica local** de HidroGest. Para la BD real basta cambiar
  `DB_URL`/`DB_USER`/`DB_PASSWORD` a un usuario de solo lectura.
- Un `DEAD_LETTER` requiere acción manual (es intencional: hay que corregir la causa primero).

---

## 21. Preguntas probables y cómo responderlas

**¿Cómo saben a qué destinos ya se envió un pago?**
En DynamoDB, tabla `pagos_entregas`: una fila por (pago, destino) con su estado y comprobante. Se
reenvía solo donde el estado no es `ENTREGADO`.

**¿Qué pasa si una API falla?**
Depende de la respuesta. Si es transitoria (5xx, 429), el mensaje va a una cola de espera con TTL
y vuelve solo, con backoff de 5 s, 30 s, 2 min y 10 min, hasta 6 intentos. Si es definitiva (400,
422, 403), va a dead-letter. Los otros destinos no se ven afectados porque cada uno tiene su propio
mensaje y su propia cola.

**¿Cómo evitan duplicar un pago si lo reenvían?**
Con la llave de idempotencia estable (`EQA-<id>`), la misma en cada intento. Si el destino ya lo
tenía, responde `409 duplicado` y lo tratamos como entregado. Además, después de un timeout primero
**consultamos** con `GET` antes de reenviar.

**¿Y si el servicio se cae a mitad de un envío?**
El mensaje no tuvo *ack*, así que RabbitMQ lo reentrega. El estado quedó `EN_VUELO` con un lease
de 30 s; al vencer, otro consumer lo retoma y consulta primero. Si el destino ya lo tenía,
`ENTREGADO` sin duplicar.

**¿Por qué no usaron Lambda como dice el diagrama?**
No hace falta: el consumer escribe directamente el resultado en DynamoDB. Una Lambda añadiría
despliegue, permisos y otro punto de fallo sin aportar nada (ADR-0001).

**¿Por qué RabbitMQ y no SQS?**
La diapositiva de la clase lo indica: "Spring Boot + RabbitMQ · productor / consumidor".

**¿Modifican la base de HidroGest?**
No. Solo `SELECT`, con un usuario sin permisos de escritura y un pool en modo solo lectura. Ni
tablas, ni migraciones, ni columna "enviado".

**¿Cómo detectan un pago que se cobra días después?**
El producer usa como cursor `updated_at` (no el `id`) y relee 10 min de traslape. Cuando el pago
pasa a `PAGADO`, su `updated_at` cambia y entra en la siguiente lectura.

**¿Por qué un mensaje por destino y no uno por pago?**
Para aislar fallos: si el ERP cae, solo esperan los mensajes del ERP. Con un mensaje por pago, un
destino caído bloquearía o reenviaría a los tres.

**¿Qué es el circuit breaker y para qué sirve?**
Si un destino falla mucho (≥50 % de las últimas 10 llamadas), el breaker se abre y deja de
llamarlo durante 30 s. Los mensajes esperan **sin gastar intentos**. Así un destino caído no manda
a dead-letter todos los pagos ni satura la API.

**¿Qué hacen los DTO generados y por qué no se generan las interfaces?**
Requisito del curso. Los DTO salen de los YAML con `openapi-generator` (solo modelos). Los clientes
`@FeignClient` se escriben a mano, cada uno con su interceptor de API key y su decodificador de
errores.

**¿Cómo manejan el dinero?**
Siempre `BigDecimal`, nunca `double`. Al banco se le envían centavos enteros (Q120.50 → 12050) y al
ERP y al tercero, texto con 2 decimales. Si un monto tuviera más de 2 decimales, falla en vez de
redondear.

**¿Qué pasa si las credenciales están mal?**
El destino responde `403` y la entrega va a `DEAD_LETTER` (no tiene sentido reintentar). Se
corrige el `.env`, se reinicia y se reencola con `/admin/.../reencolar`.

**¿Cómo se ve el estado de un pago?**
DynamoDB Admin (http://localhost:8001), `GET /admin/pagos/{id}`, o los logs filtrando por
`pago=<id>`. La colección Postman también verifica directamente en el banco y el ERP que el
comprobante coincide.

**¿Qué es un dead-letter y cómo se recupera?**
Un fallo definitivo: validación, configuración, regla de negocio o intentos agotados. Queda en
`DEAD_LETTER` con su causa en `lastErrorCode`. Tras corregir la causa:
`POST /admin/pagos/{id}/destinos/{DESTINO}/reencolar`.

**¿Qué pasa cuando llegue la tercera API real?**
Se reemplaza su YAML, se regeneran los DTO y se ajusta solo el paquete `destinos/tercero`. Producer,
consumer, estado y reconciliador no cambian.

**¿Por qué el `X-Correlation-Id`?**
Para seguir un pago en todos los logs (producer, consumer y la API del docente) con un solo
identificador.

**¿Qué pasa si se apaga RabbitMQ?**
El producer no puede publicar: el estado queda `PENDIENTE` y el reconciliador lo reencola cuando
RabbitMQ vuelve. Las colas son durables y los mensajes persistentes, así que nada se pierde.

**¿Cómo demuestran el "reenviar solo al que falta"?**
`ERP_SIM_SCENARIO=ERROR_503 docker compose up -d pagos-sender`, crear un cobro, ver en DynamoDB
Admin banco y tercero `ENTREGADO` y el ERP `REINTENTO`. Luego `docker compose up -d pagos-sender`
(sin el escenario): el ERP se entrega solo y los otros no se vuelven a llamar.

---

## 22. Glosario

| Término | Significado |
|---|---|
| **Idempotencia** | Repetir la misma operación no cambia el resultado: reenviar un pago no lo duplica. |
| **Idempotency-Key** | Identificador estable de un pago (`EQA-900010`) que el destino usa para detectar repetidos. |
| **Producer / Consumer** | Quien publica mensajes en la cola / quien los procesa. |
| **Ack** | Confirmación al broker de que el mensaje se procesó; sin ack, RabbitMQ lo reentrega. |
| **Publisher confirm** | Confirmación del broker al productor de que guardó el mensaje. |
| **TTL** | Tiempo de vida; en las colas de reintento, cuánto espera el mensaje antes de volver. |
| **DLQ / Dead-letter** | Cola y estado de los mensajes que fallaron de forma definitiva. |
| **Quorum queue** | Tipo de cola de RabbitMQ replicada y durable, recomendada para datos importantes. |
| **Lease** | "Préstamo" temporal del envío a un consumer (30 s); si muere, al vencer otro lo retoma. |
| **Escritura condicional** | Escritura en DynamoDB que solo se aplica si se cumple una condición; evita carreras. |
| **GSI disperso** | Índice secundario que solo contiene las filas que tienen el atributo indexado (`openKey`). |
| **Circuit breaker** | Corta las llamadas a un servicio que falla mucho y las retoma después. |
| **Bulkhead** | Aislar recursos por destino para que uno lento no afecte a los demás. |
| **LOST_ACK** | El destino registró el pago, pero su respuesta no llegó a tiempo. |
| **MDC** | Contexto del log; permite que cada línea lleve el correlationId y el pago. |
| **Hexagonal** | Arquitectura en la que el dominio no depende de la tecnología; esta se conecta por adaptadores. |
| **DTO** | Clase que representa el JSON de un contrato (generada desde el YAML). |
