# Pagos Sender - Microservicio de Envío de Pagos

Microservicio Spring Boot 3.5.x (JDK 17) para el envío de pagos cobrados de la base de datos HidroGest hacia 3 sistemas externos (Banco, ERP, Tercero) con arquitectura productor/consumidor sobre RabbitMQ, estado en DynamoDB y reconciliación automática.

## Arquitectura

```
HidroGest (MariaDB, solo lectura)
    │
    ▼
Producer (scheduler) ──► DynamoDB (estado PENDIENTE) ──► RabbitMQ (cola por destino)
    │                                                                      │
    ▼                                                                      ▼
Reconciliador ◄────────────────── Consumer ◄──── FeignClient ────► APIs Externas
    │                                                                      │
    └──────────────────────────── DynamoDB (estado ENTREGADO/DEAD_LETTER) ┘
```

## Requisitos Previos

- JDK 17
- Maven 3.9+
- Docker y Docker Compose
- Variables de entorno configuradas (ver `.env.example`)

## Configuración

Copie `.env.example` a `.env` y complete los valores:

```bash
cp .env.example .env
# Edite .env con sus valores reales
```

Variables críticas:
- `BANK_API_KEY`: API key del banco (obligatoria)
- `ERP_API_KEY`: API key del ERP (obligatoria)
- `TEAM_CODE`: Código de equipo (default: EQUIPO_A, prefijo EQA-)

## Ejecución Local

```bash
# Levantar toda la infraestructura
docker compose up -d

# Ver logs
docker compose logs -f pagos-sender

# Detener
docker compose down
```

### Perfil "Destino Caído" (para demo sin X-Simulation-Scenario)

```bash
docker compose --profile destino-caido up -d
```

Esto configura URLs inexistentes para demostrar reintentos, circuit breaker y DLQ.

## Endpoints

- `GET /actuator/health` - Health check completo (BD, RabbitMQ, DynamoDB, APIs externas)
- `GET /actuator/metrics` - Métricas Prometheus
- `GET /actuator/prometheus` - Exportador Prometheus

## Flujo de Trabajo

### Producer
1. Cada `PRODUCER_INTERVALO` (default 10s) consulta pagos `PAGADO` con `updated_at >= cursor - TRASLAPE`
2. Para cada pago y destino activo, crea estado `PENDIENTE` en DynamoDB (put condicional)
3. Si se crea, publica mensaje en RabbitMQ con `publisher confirms`
4. Avanza cursor al máximo `updated_at` del lote

### Consumer (por mensaje)
1. Carga estado; si terminal (`ENTREGADO`/`DEAD_LETTER`) → ack y sale
2. Reclama con lease (`EN_VUELO`); si falla → otro consumer lo tiene: ack
3. Lee pago de MariaDB y valida localmente
4. Si estado previo `DESCONOCIDO` → lookup por llave; si existe → `ENTREGADO`
5. Envía vía gateway del destino → clasifica resultado (tabla de decisión)
6. Registra intento en `pagos_intentos` y actualiza estado
7. Ack del mensaje

### Reconciliador
- Cada `RECONCILIADOR_INTERVALO` (default 2m):
  - Reencola estados con `nextAttemptAt` vencido
  - Reencola `EN_VUELO` con lease vencido
  - Detecta `PENDIENTE` sin actividad (caída put/publish)
  - Barrido de consistencia: pagos `PAGADO` sin estado para destinos activos
- `DEAD_LETTER` no se reintenta automáticamente (requiere acción manual)

## Idempotencia

- Llave estable: `EQA-000123` (prefijo derivado de `TEAM_CODE` + id pago 6 dígitos)
- Misma llave en los 3 destinos (header `Idempotency-Key` en Banco/Tercero, `documento_origen.numero` en ERP)
- Nunca UUID nuevo por reintento

## Tabla de Decisión (Respuesta → Acción)

| HTTP / Code | Resultado | Acción |
|---|---|---|
| 201 | DELIVERED | Guardar comprobante, estado ENTREGADO |
| 409 DUPLICATE/DOCUMENTO_DUPLICADO | DELIVERED | Tratar como entregado, tomar comprobante original |
| 409 REUSED/DOCUMENTO_REUTILIZADO | DEAD_LETTER | Bug productor: sin reintento |
| 400, 403, 422 | DEAD_LETTER | Sin reintento |
| 429, 500, 502, 503 | RETRY | Backoff + Retry-After |
| 504, timeout, red error | UNKNOWN | Primero lookup (GET); si no existe → reintentar POST |

## Pruebas

```bash
# Tests unitarios e integración
mvn test

# Verificar DTOs generados
mvn clean compile
ls target/generated-sources/openapi/
```

## Estructura del Proyecto

```
src/main/java/gt/edu/umg/desarrolloweb/pagos/
├── domain/                    # Modelos, enums, puertos (hexagonal)
├── application/               # Casos de uso: Dispatcher, Processor, Reconciliation
├── infrastructure/
│   ├── mariadb/               # JdbcTemplate (solo lectura)
│   ├── dynamodb/              # Adaptadores estado/intentos/control
│   ├── rabbit/                # Topología, publisher, listeners
│   ├── destinos/
│   │   ├── banco/             # DTOs, FeignClient, mapper, gateway
│   │   ├── erp/               # DTOs, FeignClient, mapper, gateway
│   │   └── tercero/           # DTOs, FeignClient, mapper, gateway
│   └── config/                # Configuración, health, métricas, resilience
└── PagosSenderApplication.java
```

## Decisiones Documentadas (ADRs)

- **ADR-001**: RabbitMQ sobre SQS (requisito de clase)
- **ADR-002**: Sin Lambdas; consumer escribe directamente en DynamoDB
- **ADR-003**: DynamoDB para estado; HidroGest solo lectura sin JPA
- **ADR-004**: Tablas DynamoDB creadas automáticamente en perfil `dev`
- **ADR-005**: `periodo_fiscal` del ERP = mes del pago (cuándo se contabiliza)
- **ADR-006**: Retry tiers no bloqueantes con colas TTL + DLX

## Runbook (Operaciones)

### DLQ
```bash
# Ver mensajes en DLQ
docker exec pagos-rabbitmq rabbitmqadmin get queue=pagos.dlq

# Reencolar manualmente tras corregir causa
curl -X POST http://localhost:8080/admin/requeue-dlq \
  -H "Content-Type: application/json" \
  -d '{"paymentId": 123, "destino": "ERP}'
```

### Reconciliación Manual
```bash
# Verificar pagos pendientes
curl http://localhost:8080/actuator/metrics/pagos.pending

# Forzar reconciliación
curl -X POST http://localhost:8080/admin/reconcile
```

## CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`):
- Build + test en cada push/PR
- Verifica `mvn clean verify`
- Publica imagen Docker en registry

## Licencia

Proyecto académico - Desarrollo Web 2026 (UMG Jutiapa)