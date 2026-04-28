# Multi-stage Dockerfile for building a Spring Boot app with Gradle
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew clean bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
