# Contexto de construcción: la raíz del repo, porque el pom toma los scripts SQL de ../database.

FROM maven:3.9-eclipse-temurin-21 AS construccion

WORKDIR /build

# Solo el pom primero: la capa de dependencias se reutiliza mientras no cambie.
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml dependency:go-offline

COPY backend/src backend/src
COPY database database

# package corre las pruebas (perfil test, sin BD): un fallo detiene la imagen.
RUN mvn -B -f backend/pom.xml clean package

FROM eclipse-temurin:21-jre-alpine AS ejecucion

# curl para el HEALTHCHECK; tzdata para America/Guatemala.
RUN apk add --no-cache curl tzdata

RUN addgroup -g 10001 -S chn \
 && adduser  -u 10001 -S chn -G chn -h /app -s /sbin/nologin

WORKDIR /app

COPY --from=construccion /build/backend/target/prestamos.jar /app/prestamos.jar
RUN chown -R chn:chn /app

USER chn:chn

ENV TZ=America/Guatemala \
    SERVER_PORT=8081

EXPOSE 8081

# start-period cubre las migraciones de Flyway en el primer arranque.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

# Forma exec: el JVM es PID 1 y recibe SIGTERM para el apagado ordenado.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Duser.timezone=America/Guatemala", \
            "-Dfile.encoding=UTF-8", \
            "-jar", "/app/prestamos.jar"]
