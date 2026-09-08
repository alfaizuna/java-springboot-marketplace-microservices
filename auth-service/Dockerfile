# ==============================================================================
# Multi-stage Dockerfile — auth-service
# Built from the Maven multi-module root context (context: .)
# ==============================================================================

# Stage 1: Build — compile only auth-service and its dependencies
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 1. Copy root pom + all child poms first (layer caching for dependencies)
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY order-service/pom.xml order-service/
COPY payment-webhook-service/pom.xml payment-webhook-service/

# 2. Download all dependencies offline (cached layer)
RUN mvn dependency:go-offline -B -pl auth-service -am

# 3. Copy source code
COPY auth-service/src auth-service/src

# 4. Build only auth-service JAR
RUN mvn clean package -DskipTests -B -pl auth-service -am

# Stage 2: Minimal runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/auth-service/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser:appgroup

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
