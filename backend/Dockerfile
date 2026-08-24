# Build context is the repo root (needs ../contracts/openapi.yaml for codegen).
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY contracts ./contracts
COPY backend/pom.xml backend/pom.xml
WORKDIR /workspace/backend
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
