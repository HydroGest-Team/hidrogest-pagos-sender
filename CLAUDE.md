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

# Pruebas (unitarias; surefire por defecto)
mvn test
mvn test -Dtest=NombreDeLaClaseTest          # una sola clase
mvn test -Dtest=NombreDeLaClaseTest#metodo   # un solo método

# Build completo con pruebas (criterio de "terminado")
mvn clean verify

# Empaquetar sin pruebas (lo que hace el Dockerfile)
mvn clean package -DskipTests

# Perfiles Maven definidos en pom.xml: dev (por defecto), prod, destino-caido
mvn clean verify -Pdestino-caido
```

### Entorno local (Docker Compose)

```bash
cp .env.example .env            # completar valores reales solo en .env
docker compose up -d            # rabbitmq, mariadb (+ mariadb-init), dynamodb-local,
                                # third-api-mock y pagos-sender
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
```

## Estructura

- Paquete base: `gt.edu.umg.desarrolloweb.pagos` (hexagonal: `domain/`,
  `application/`, `infrastructure/{mariadb,dynamodb,rabbit,destinos/{banco,erp,tercero},config}`).
- Configuración: `src/main/resources/application.yml` (todo por variables de entorno).
- Seed de MariaDB local: `docker/mariadb/init.sql`.
- Mock de la tercera API: `docker/third-mock/mock_third_api.py`.
- ADRs: `docs/adr/`.

## Estado actual conocido (verificado el 2026-10-23)

- `src/test/` está vacío: todavía no hay pruebas, así que `mvn test` no valida nada.
- `docs/adr/` está vacío, aunque el README lista ADR-001 a ADR-006.
- `.github/workflows/` está vacío, aunque el README menciona `ci.yml`.
- El README documenta `docker compose --profile destino-caido`, pero
  `docker-compose.yml` no define perfiles; `destino-caido` solo existe como perfil Maven.
- `src/main/resources/db/migration/` existe vacío; no debe usarse contra HidroGest.

## Convenciones

- Código e identificadores en inglés o en español, según lo que ya existe; comentarios,
  README, ADR y mensajes al usuario en español.
- Dinero siempre `BigDecimal`; fechas de HidroGest en `America/Guatemala`.
- Nunca enviar el DPI ni registrar `x-api-key`, cuerpos completos o datos personales.
- Commits con Conventional Commits.
