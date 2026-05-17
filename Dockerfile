# Stage 1: Build the JAR using Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies separat cachen (schneller bei Source-only Änderungen)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Source kopieren und bauen
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Schlankes Runtime-Image mit JRE 21
FROM eclipse-temurin:21-jre
WORKDIR /app

# wget für Health Check installieren
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

# Nur das gebaute JAR aus Stage 1 übernehmen
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
