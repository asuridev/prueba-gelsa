# Imagen del servicio, en tres etapas.
#
# El corte no es cosmético: las dependencias se resuelven en una etapa propia que
# solo se invalida si cambia build.gradle, de modo que iterar sobre src/ no vuelve
# a descargar medio Maven Central. Y el jar se explota en las capas de Spring Boot
# (dependencies / spring-boot-loader / snapshot-dependencies / application), que es
# lo que hace que un cambio de codigo reescriba unos kilobytes de capa y no los
# cincuenta megas del jar entero.
#
# El contexto de build es la RAIZ del proyecto, no deploy/: se necesita src/ y el
# wrapper de Gradle vendorizado. Por eso el .dockerignore vive en la raiz.

# ── 1. Dependencias (capa cacheada) ──────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS deps
WORKDIR /workspace
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
# Calentar la cache de Gradle. Best effort: si la resolucion falla aqui (una
# dependencia que solo existe en un repo privado, por ejemplo) no se rompe la
# imagen, simplemente se descargara en la etapa siguiente.
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null 2>&1 || true

# ── 2. Compilacion y explotado en capas ──────────────────────────────────────
FROM deps AS build
WORKDIR /workspace
COPY src/ src/
RUN ./gradlew --no-daemon bootJar -x test
RUN cp build/libs/payment-spring-0.1.4.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ── 3. Runtime ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /application

# Usuario sin privilegios. La app no escribe en ningun volumen, asi que esto no
# choca con el mapeo de UIDs de podman rootless. El directorio es suyo solo para
# que el entrenamiento de abajo pueda dejar ahi la cache AOT.
RUN addgroup -S app && adduser -S -G app app && chown app:app /application
USER app

# El orden importa: de lo que menos cambia a lo que mas.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

# ── Cache AOT (Java 25, JEP 483/514/515) ─────────────────────────────────────
# Un arranque de entrenamiento deja en app.aot las clases ya cargadas y enlazadas
# y los perfiles de los metodos calientes; la JVM de verdad arranca desde ahi.
# spring.context.exit=onRefresh corta al terminar de refrescar el contexto, antes
# de arrancar listeners, @Scheduled y runners. El build no tiene base ni broker,
# asi que se entrena con develop (el perfil de este compose) mas aot-training,
# que apaga lo unico que hablaria con ellos al instanciar los singletons
# (src/main/resources/application-aot-training.yaml).
#
# -Xlog:aot=error calla los cientos de avisos de clases que la cache no admite
# (proxies, CGLIB): son esperables y no cambian nada.
#
# Si el entrenamiento falla (un bean que conecta al construirse) la imagen sale
# igual, sin cache: es una optimizacion, no una condicion para arrancar. Se apaga
# con --build-arg AOT_CACHE=false. La cache solo vale con las mismas banderas de
# la JVM: un JAVA_OPTS distinto en ejecucion no rompe nada, la ignora.
#
# JVM_BASE_OPTS son las banderas de la imagen, y JAVA_OPTS las de quien la
# despliega: separadas para que un JAVA_OPTS con -Xmx no se lleve por delante las
# de aqui (ni, con ellas, la cache). Cabeceras de objeto compactas (JEP 519): 8
# bytes por objeto en vez de 12; con job-dispatch el heap vivo baja un 10 %.
ENV JVM_BASE_OPTS="-XX:+UseCompactObjectHeaders"
ARG AOT_CACHE=true
RUN if [ "$AOT_CACHE" = "true" ]; then \
      java $JVM_BASE_OPTS $JAVA_OPTS -XX:AOTCacheOutput=app.aot -Xlog:aot=error -Dspring.context.exit=onRefresh \
        -Dspring.profiles.active=develop,aot-training -jar application.jar \
      || { rm -f app.aot; echo 'AVISO: el entrenamiento de la cache AOT fallo; la imagen arranca sin ella.'; }; \
    fi

EXPOSE 8080

# wget de BusyBox (viene en la base alpine): la imagen no trae curl, y este
# healthcheck lo honran igual docker y podman. Es lo que consume el
# `depends_on: service_healthy` de quien dependa de la app y el bucle de up.sh.
# /readyz y no /actuator/health/readiness: es la misma sonda, pero servida en el puerto
# PRINCIPAL en todos los perfiles, y en production el actuator vive en otro puerto.
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=18 \
  CMD wget -q -O - http://localhost:8080/readyz | grep -q '"status":"UP"' || exit 1

# Forma shell para que $JAVA_OPTS se expanda; exec para que la JVM sea el PID 1 y
# reciba el SIGTERM que dispara el apagado ordenado (server.shutdown: graceful).
# La cache solo se pasa si existe: sin el archivo la JVM arranca igual, pero
# escribe tres lineas [error][aot] que mandan a buscar un fallo que no hay.
ENTRYPOINT ["sh", "-c", "exec java $([ -f app.aot ] && echo -XX:AOTCache=app.aot) $JVM_BASE_OPTS $JAVA_OPTS -jar application.jar"]
