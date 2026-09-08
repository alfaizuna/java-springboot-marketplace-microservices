# ==============================================================================
# Multi-stage Dockerfile — api-gateway
# Built from the Maven multi-module root context (context: .)
# ==============================================================================

# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY order-service/pom.xml order-service/
COPY payment-webhook-service/pom.xml payment-webhook-service/

RUN mvn dependency:go-offline -B -pl api-gateway -am

COPY api-gateway/src api-gateway/src

RUN mvn clean package -DskipTests -B -pl api-gateway -am

# Stage 2: Minimal runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/api-gateway/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser:appgroup

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
