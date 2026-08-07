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
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the compiled JAR file from the build stage
COPY --from=build --chown=appuser:appgroup /app/target/vex360-0.0.1-SNAPSHOT.jar app.jar

USER appuser

ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-Xmx192m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=48m -Xss256k"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
