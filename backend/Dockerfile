FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipUnitTests=true

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S worksphere && adduser -S worksphere -G worksphere
WORKDIR /app

COPY --from=build /workspace/target/worksphere-backend-*.jar app.jar

USER worksphere
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
