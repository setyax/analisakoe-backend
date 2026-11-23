# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Cache dependency layer
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy source & build
COPY src ./src
RUN mvn -B -q -DskipTests package

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/target/analisakoe-*.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8081/api/ping || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]