# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy maven wrapper and pom.xml first to leverage Docker layer caching for dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Fix line endings for mvnw in case the build runs on/from Windows
RUN tr -d '\r' < mvnw > mvnw.lf && mv mvnw.lf mvnw && chmod +x mvnw

# Resolve dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy the source code and build the package (excluding tests for faster builds)
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the compiled JAR file from the build stage
COPY --from=build /app/target/vex360-0.0.1-SNAPSHOT.jar app.jar

# Render automatically exposes and sets the PORT environment variable.
# We set SERVER_PORT so Spring Boot binds to the correct port specified by Render.
ENV PORT=8080
ENV SERVER_PORT=${PORT}

EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
