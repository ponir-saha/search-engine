# Multi-stage Dockerfile for building and running the Spring Boot app

# Build stage
FROM maven:3.10.1-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy sources and build
COPY pom.xml mvnw .mvn/ ./
COPY src ./src
# Ensure mvnw is executable if present
RUN if [ -f mvnw ]; then chmod +x mvnw; fi
RUN mvn -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the packaged jar from the build stage
COPY --from=build /workspace/target/search-engine-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

