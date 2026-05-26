# --- Stage 1: Build ---
# Using the specific image version for consistency
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml separately to cache dependencies
COPY pom.xml .

# Optimization: Use BuildKit cache mount to persist the .m2 repository
# This prevents re-downloading dependencies on every build
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -DskipTests

# Copy the source code
COPY src ./src

# Optimization: Use the same cache mount for the build phase
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]