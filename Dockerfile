# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY src ./src

RUN chmod +x mvnw && ./mvnw -B package && \
    cp target/retail-engine-1.0.0-SNAPSHOT.jar app.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
