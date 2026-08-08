# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build --chown=appuser:appgroup \
    /app/target/*.jar app.jar

USER appuser

ENV JAVA_OPTS="-XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -Xss512k \
  -Xms64m -Xmx160m \
  -XX:MaxMetaspaceSize=128m \
  -XX:ReservedCodeCacheSize=32m \
  -XX:MaxDirectMemorySize=48m \
  -XX:+ExitOnOutOfMemoryError \
  -Dserver.tomcat.threads.max=20 \
  -Dserver.tomcat.threads.min-spare=2"

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-10000} -jar app.jar"]