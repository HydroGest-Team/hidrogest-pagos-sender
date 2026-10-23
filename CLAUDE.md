# CLAUDE.md — pagos-sender (Feature 3: envío de pagos)

Contexto completo, contratos resumidos, arquitectura y plan por fases:

@docs/CONTEXTO_Y_PLAN_FEATURE3_PAGOS.md

Ante cualquier conflicto entre ese documento y un YAML de contrato, **gana el YAML**.

## Reglas obligatorias

1. **Los YAML de contrato no se modifican.** Ni `docs/*-api.yaml` ni sus copias en
   `src/main/resources/openapi/`. Ambas copias deben quedar idénticas
   (comprobar con `cmp`). Si la generación no sirve, se cambia la configuración del
   `openapi-generator-maven-plugin` en `pom.xml`, nunca el YAML ni el código generado.
2. **Solo DTO, sin interfaces de API generadas.** Cada ejecución del plugin mantiene
   `generateApis=false`, `generateModels=true`, `generateSupportingFiles=false`.
   Las interfaces `@FeignClient` se escriben a mano. El código generado vive en
   `target/generated-sources/openapi/` y no se edita ni se versiona.
3. **Tecnologías prohibidas:**
   - Sin AWS Lambda (el consumer escribe directamente en DynamoDB).
   - Sin SQS (la cola es RabbitMQ).
   - Sin JPA/Hibernate sobre la BD de HidroGest: acceso **solo lectura** con
     `JdbcTemplate`. No crear tablas, migraciones ni sentencias
     `INSERT`/`UPDATE`/`DELETE`/DDL contra esa BD.
4. **Secretos solo en `.env`**, que está en `.gitignore`. Nunca escribir llaves,
   contraseñas ni tokens en código, `application.yml`, `pom.xml`, `docker-compose.yml`,
   README, ADR, pruebas ni logs. `.env.example` lleva solo placeholders.
   No mostrar el contenido de `.env` en el chat.
5. **Antes de dar una tarea por terminada**, ejecutar y revisar la salida de:
   ```bash
   mvn clean verify
   docker compose up -d
   docker compose ps        # todos los servicios healthy / completed
   ```
   Si alguno falla, la tarea **no** está terminada: corregir o informar el error tal cual.
6. **Nunca afirmar que algo está probado, compila o funciona si no se ejecutó.**
   Al cerrar cada tarea, separar explícitamente: qué se ejecutó (con su resultado),
   qué no se pudo ejecutar y por qué.

## Comandos del proyecto

No hay Maven Wrapper (`mvnw`): se usa `mvn` del sistema (Maven 3.9+, JDK 17).

```bash
# Compilar y generar los DTO de los 3 contratos
mvn clean compile
ls target/generated-sources/openapi/{banco,erp,tercero}

# Pruebas unitarias (*Test, surefire)
mvn test
mvn test -Dtest=NombreDeLaClaseTest          # una sola clase
mvn test -Dtest=NombreDeLaClaseTest#metodo   # un solo método

# Integración (*IT, failsafe + Testcontainers: MariaDB y DynamoDB Local; requiere Docker)
mvn -Dit.test=MariaDbPaymentSourceIT failsafe:integration-test

# Build completo con unitarias + integración (criterio de "terminado")
mvn clean verify

# Empaquetar sin pruebas (lo que hace el Dockerfile)
mvn clean package -DskipTests
```

### Entorno local (Docker Compose)

```bash
cp .env.example .env            # completar valores reales solo en .env
docker compose up -d            # rabbitmq, mariadb, dynamodb-local, third-api-mock y pagos-sender
docker compose ps
docker compose logs -f pagos-sender
docker compose down
```

Puertos: app `8080`, RabbitMQ `5672` / management `15672`, MariaDB `3306`,
DynamoDB Local `8000`, mock de la tercera API `8089`.

Comprobaciones rápidas:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8089/third/v1/health
# Estado por destino de un pago (Basic Auth con ADMIN_PASSWORD de .env)
curl -s -u admin:$ADMIN_PASSWORD http://localhost:8080/admin/pagos/900001
# Simular a HidroGest (escribe con el usuario de la app, no con el de este servicio)
docker/mariadb/simular-cobro.sh 900006
docker/mariadb/nuevo-cobro.sh 135.25 Debito
# Forzar escenarios en los simuladores
BANK_SIM_SCENARIO=LOST_ACK ERP_SIM_SCENARIO=ERROR_503 docker compose up -d pagos-sender
```

El Banco y el ERP son los simuladores reales del docente (AWS); no van en el compose.

## Estructura

- Paquete base: `gt.edu.umg.desarrolloweb.pagos` (hexagonal: `domain/`,
  `application/`, `infrastructure/{mariadb,dynamodb,rabbit,destinos/{banco,erp,tercero},config}`).
- Configuración: `src/main/resources/application.yml` (todo por variables de entorno).
- MariaDB local: `docker/mariadb/01-esquema-hidrogest.sql` (copiado de las migraciones de
  Laravel; si HidroGest cambia su esquema, actualizar aquí) y `02-datos-semilla.sql` (fechas fijas,
  pagos desde 900001: no cambiar datos de un pago ya enviado o los simuladores responden
  `IDEMPOTENCY_KEY_REUSED`).
- Mock de la tercera API: `docker/third-mock/mock_third_api.py`.
- ADRs: `docs/adr/` · Operación: `docs/RUNBOOK.md` · CI: `.github/workflows/ci.yml`.
- `src/main/resources/db/migration/` existe vacío; no debe usarse contra HidroGest.

## Gotchas

- El `pom.xml` no hereda de `spring-boot-starter-parent`: versiones de plugins fijadas a mano y
  `-parameters` activado en el compilador (sin él fallan `@PathVariable`/`@RequestParam`).
- El BOM de Testcontainers va **antes** que el de Spring Boot: Docker Engine 29 exige API ≥ 1.44.
- Los `*FeignConfig` no llevan `@Configuration` a propósito: si lo tuvieran, sus interceptores
  (con la API key) se aplicarían a todos los clientes.

## Convenciones

- Código e identificadores en inglés o en español, según lo que ya existe; comentarios,
  README, ADR y mensajes al usuario en español.
- Dinero siempre `BigDecimal`; fechas de HidroGest en `America/Guatemala`.
- Nunca enviar el DPI ni registrar `x-api-key`, cuerpos completos o datos personales.
- Commits con Conventional Commits.
