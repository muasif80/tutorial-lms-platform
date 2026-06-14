# --- build stage: compile the jar with Maven + JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# --- runtime stage: a small JRE image, hardened ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl backs the container HEALTHCHECK; run as a non-root user (least privilege)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 scholr
COPY --from=build /app/target/lms-platform.jar app.jar
USER scholr
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
# readiness probe — "UP" only once the DB is connected and Flyway migrations have applied
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
