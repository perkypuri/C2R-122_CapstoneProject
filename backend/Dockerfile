# --- build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# --- run stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/healthBacked.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
