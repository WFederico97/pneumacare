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

LABEL org.opencontainers.image.source="https://github.com/WFederico97/pneumacare"
LABEL org.opencontainers.image.description="Pneumacare – Spring Boot 4 microservice"
LABEL org.opencontainers.image.licenses="MIT"

RUN addgroup --system --gid 1001 pneumacare \
 && adduser --system --uid 1001 --gid 1001 --no-create-home pneumacare

COPY --from=build --chown=pneumacare:pneumacare /app/target/*.jar app.jar

USER pneumacare
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

