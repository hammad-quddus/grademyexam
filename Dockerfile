# ==========================================
# Stage 1: Build the application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of the source code and build the fat JAR
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Create the production runtime image
# ==========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Cloud Run sets the PORT environment variable automatically (defaults to 8080)
EXPOSE 8080

# Copy the compiled JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Run the application with optimized memory settings for container environments
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]