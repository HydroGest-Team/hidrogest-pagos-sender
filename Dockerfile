# syntax=docker/dockerfile:1
# --- Build: compila y genera los DTO desde los contratos (sin pruebas; esas corren con mvn verify) ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# --- Runtime: JRE mínimo, usuario sin privilegios ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S pagos && adduser -S pagos -G pagos
COPY --from=build /build/target/pagos-sender-*.jar app.jar
USER pagos
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
