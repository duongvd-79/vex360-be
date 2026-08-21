# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN tr -d '\r' < mvnw > mvnw.lf && mv mvnw.lf mvnw && chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -Dmaven.test.skip=true package

# ---------- Run stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build --chown=appuser:appgroup \
    /app/target/*.jar app.jar

USER appuser

# JVM tuning for a 1 CPU / 2GB (Render Standard) container, always-on:
#   - G1 (JDK 21 default, no override) fits a ~1GB heap much better than Serial
#   - No TieredStopAtLevel cap: full JIT (C1+C2) now that CPU isn't throttled
#   - Default stack size: no memory pressure to justify shrinking it
#   - Xms = Xmx: avoids heap-resize overhead, predictable footprint
#   - Headroom left: ~1024m heap + 256m metaspace + 128m codecache + 128m
#     direct = ~1536m of 2048m, leaving ~512m for OS/thread stacks/native overhead
ENV JAVA_OPTS="-XX:+ExitOnOutOfMemoryError \
  -Xms1024m -Xmx1024m \
  -XX:MaxMetaspaceSize=256m \
  -XX:ReservedCodeCacheSize=128m \
  -XX:MaxDirectMemorySize=128m \
  -Dserver.tomcat.threads.max=100 \
  -Dserver.tomcat.threads.min-spare=10"

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-10000} -jar app.jar"]