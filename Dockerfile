# -- Build ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /app

# Dependencies first, so a code-only change reuses this layer.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests already ran in CI (and need Docker for Testcontainers, which the build has not).
RUN mvn -B -q package -DskipTests

# -- Runtime -------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# wget for the healthcheck; installed before dropping root.
RUN apk add --no-cache wget \
    && addgroup -S spring && adduser -S spring -G spring

USER spring

COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Inside the container the API always listens on 8081; the host port is chosen in docker-compose.yml.
ENV SERVER_PORT=8081
EXPOSE 8081

# /actuator/health is the only public Actuator endpoint. The start period covers
# Flyway migrations on a fresh database.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1

# The JVM sizes its heap from the container memory limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
