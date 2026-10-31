# pagos-sender · Feature 3: envío de pagos a sistemas externos

Microservicio **Spring Boot 3.5 (JDK 17)** que toma los pagos cobrados en **HidroGest** (MariaDB,
solo lectura) y los entrega a **3 sistemas externos**: el **Banco** y el **ERP** (simuladores del
docente en AWS) y una **tercera API** (contrato provisional). Usa productor/consumidor sobre
**RabbitMQ**, guarda el estado por destino en **DynamoDB** y reenvía **solo a los destinos que faltan**.

Contexto completo y plan: [`docs/CONTEXTO_Y_PLAN_FEATURE3_PAGOS.md`](docs/CONTEXTO_Y_PLAN_FEATURE3_PAGOS.md) ·
Guía completa (qué, cómo, dónde y por qué): [`docs/GUIA-COMPLETA.md`](docs/GUIA-COMPLETA.md) · Operación: [`docs/RUNBOOK.md`](docs/RUNBOOK.md) · Decisiones: [`docs/adr/`](docs/adr/)

## Arquitectura

```
 HidroGest (MariaDB, SOLO SELECT)
        │  Producer (cada 10 s, cursor por updated_at + traslape)
        ▼
 DynamoDB pagos_entregas ── PENDIENTE por (pago, destino) ──► RabbitMQ pagos.exchange
                                                               │
                  ┌──────────────── pagos.banco / pagos.erp / pagos.tercero (quorum)
                  ▼
           Consumer por destino ──Feign──► Banco · ERP · Tercero
            │  ▲      │
            │  │      └─► DynamoDB: estado + bitácora pagos_intentos
            │  └── pagos.<d>.retry.{5s,30s,2m,10m} (TTL)   ·   pagos.<d>.dlq
            ▼
 Reconciliador (cada 1 min + barrido cada 10 min): reencola solo lo vencido o faltante
```

- **Un mensaje por (pago, destino):** si el ERP cae, banco y tercero siguen.
- **Llave estable** `EQA-000123` (prefijo derivado de `TEAM_CODE`): header `Idempotency-Key` en banco
  y tercero; `documento_origen.numero` en el ERP. Nunca un UUID por intento.
- **Tabla de decisión** común: `201`/`409 duplicado` → entregado · `400`/`403`/`422`/`409 reutilizado`
  → dead-letter · `429`/`5xx` → reintento con `Retry-After` · timeout/`504` → **desconocido**: el
  siguiente intento consulta (`GET`) antes de reenviar.
- **Circuit breaker por destino**: con el destino caído no se gastan intentos.
- Sin Lambdas, sin SQS, sin JPA, sin reintentos ocultos de Feign. DTO generados de los YAML
  (sin interfaces de API); clientes Feign escritos a mano.

Código (hexagonal) en `src/main/java/gt/edu/umg/desarrolloweb/pagos/`: `domain/` (modelo y puertos, sin
Spring), `application/` (producer, consumer, reconciliador, clasificador), `infrastructure/`
(`mariadb`, `dynamodb`, `rabbit`, `destinos/{banco,erp,tercero}`, `admin`, `config`, `scheduling`).

## Requisitos

JDK 17, Maven 3.9+, Docker con Docker Compose. Acceso a internet (Maven Central y el API Gateway del docente).

## Puesta en marcha

```bash
cp .env.example .env      # completar: API keys del equipo, BANK_BASE_URL/ERP_BASE_URL y contraseñas locales
docker compose up -d      # RabbitMQ, MariaDB (esquema de HidroGest + semilla), DynamoDB Local, mock del tercero y el servicio
docker compose ps         # todo "healthy"
docker compose logs -f pagos-sender
```

Al arrancar, el producer encola los pagos semilla `900001`–`900005` y los envía al banco y ERP
**reales** y al mock del tercero. Para ver el resultado:

```bash
export ADMIN_PASSWORD=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
curl -s -u admin:$ADMIN_PASSWORD http://localhost:8080/admin/pagos/900001 | jq
```

Simular a HidroGest: `docker/mariadb/nuevo-cobro.sh 135.25 Debito` (cobro nuevo; con `pendiente` al final
queda NO PAGADO) y `docker/mariadb/simular-cobro.sh <id>` (cobrarlo después). Escenarios de fallo: ver el runbook.

### Pruebas con interfaz gráfica

Guía paso a paso: [`docs/PRUEBAS-GRAFICAS.md`](docs/PRUEBAS-GRAFICAS.md).

| Interfaz | URL | Uso |
|---|---|---|
| Adminer | http://localhost:8081 | BD de HidroGest; registrar/cobrar pagos con las rutinas `simular_nuevo_cobro` y `simular_cobro_tardio` |
| DynamoDB Admin | http://localhost:8001 | Estado por destino (`pagos_entregas`) y bitácora (`pagos_intentos`) |
| RabbitMQ | http://localhost:15672 | Colas, reintentos en espera y dead-letter |
| Postman | `docs/postman/` | Estado del pago y verificación directa en el banco y el ERP (24 asserts) |

Para correr desde el IDE contra el compose: `docker compose up -d rabbitmq mariadb dynamodb-local third-api-mock`
y `mvn spring-boot:run` con las variables de `.env` (los hosts por defecto son `localhost`).

## Build y pruebas

```bash
mvn clean verify     # compila, genera los DTO, 63 unitarias + 11 de integración (Testcontainers: MariaDB, DynamoDB Local)
mvn test             # solo unitarias
mvn -Dit.test=MariaDbPaymentSourceIT failsafe:integration-test   # una prueba de integración
```

## Variables principales (`.env`)

| Variable | Uso |
|---|---|
| `TEAM_CODE` | `EQUIPO_A`…`EQUIPO_D`; debe coincidir con la API key |
| `BANK_BASE_URL`, `BANK_API_KEY` | Simulador del banco (header `x-api-key`) |
| `ERP_BASE_URL`, `ERP_API_KEY` | Simulador del ERP |
| `THIRD_BASE_URL`, `THIRD_API_KEY` | Tercera API (en compose: el mock) |
| `DESTINOS_ACTIVOS` | `BANCO,ERP,TERCERO` |
| `*_SIM_SCENARIO` | `X-Simulation-Scenario` (vacío = no se envía) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | HidroGest; usuario **solo SELECT** |
| `DYNAMO_ENDPOINT` | Vacío = AWS real; con valor = DynamoDB Local |
| `ADMIN_USER`, `ADMIN_PASSWORD` | `/admin` y métricas (vacío = cerrado) |

Los demás (intervalos, tiers de reintento, concurrencia por destino) están en
`src/main/resources/application.yml` con su variable de entorno.

## Pendientes que dependen del docente

- **D4** · Quién crea las tablas DynamoDB en AWS (hoy: DynamoDB Local, creación automática).
- **D5** · `teamCode`: el banco y el ERP aceptaron `EQUIPO_A` con la llave configurada (201 el 2026-10-23).
- **D7** · URL/llave propias del ERP: hoy usa el mismo API Gateway y la misma llave que el banco, y funciona.
- **Tercera API real**: contrato provisional + mock ([ADR-0006](docs/adr/0006-tercera-api-provisional.md)).

Proyecto académico · Desarrollo Web 2026 · UMG Jutiapa.
