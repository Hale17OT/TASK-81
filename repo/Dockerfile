# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Keystore password passed at build time; must match SERVER_SSL_KEY_STORE_PASSWORD at runtime
ARG KEYSTORE_PASSWORD=changeit

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY scripts ./scripts

RUN chmod +x scripts/generate-keystore.sh && \
    KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD} bash scripts/generate-keystore.sh

RUN mvn package -DskipTests -B

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/campusstore-1.0.0.jar app.jar
COPY --from=build /app/keystore.p12 keystore.p12

EXPOSE 8443

ENTRYPOINT ["java", "-jar", "app.jar"]
