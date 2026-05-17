# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

LABEL org.opencontainers.image.source="https://github.com/WFederico97/backend-java-template"
LABEL org.opencontainers.image.description="Backend Java Core Template – Spring Boot 4 microservice skeleton"
LABEL org.opencontainers.image.licenses="MIT"

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

