# order-spring

Recibe los pedidos de los compradores, calcula su importe con la copia local del catálogo, pide el cobro a payment, sigue su desenlace y lo notifica al comprador a través de notifications.

Generado desde `specs/order` v0.1.0 por keel-spring 0.4.0 (scaffolding transversal al stack).

## Ejecutar

```bash
bash infra/up.sh   # infraestructura de prueba
bash infra/init-keycloak.sh   # siembra el realm de prueba (roles, usuarios, clientes); otra vez tras cada up.sh
./gradlew bootRun
./gradlew build -x test        # compilación y empaquetado (no ejecuta pruebas)
./gradlew integrationTest      # escenarios FL-* contra la infraestructura de arriba
./gradlew test                 # contextLoads(): el contexto arranca con perfil test (H2, sin infra)
```

Requiere Java 25 (el wrapper de Gradle va incluido; en Windows usa `gradlew.bat`).

## Infraestructura de prueba

Elegida en el cuestionario de `keel-spring build` y persistida en `keel-stack.json`
(bórralo y re-ejecuta el build con `--force` para cambiarla).

| Servicio | Imagen | Puerto |
|---|---|---|
| PostgreSQL | postgres:16-alpine | 5432 |
| RabbitMQ | rabbitmq:4-management | 5672 / 15672 (UI) |
| Keycloak (contenedor de prueba) | quay.io/keycloak/keycloak:26.3.1 | 8180 |
| WireMock (proveedor de prueba de las integraciones salientes) | wiremock/wiremock:3.13.1 | 8080 |
| devtools (validación) | alpine:3.20 (build local) | — (interno) |

El perfil `test` usa H2 en memoria (no necesita contenedores): queda listo para la suite de pruebas unitarias, que es un proceso posterior a la validación funcional.

## Validación de infraestructura

Todo lo relativo a la infraestructura de prueba vive en `infra/`. Antes de ejercitar
los escenarios funcionales, levántala y sondéala (con podman, exporta `CONTAINER_RUNTIME=podman`):

```bash
bash infra/up.sh               # resuelve runtime y frontend de compose, y levanta
bash infra/init-keycloak.sh   # siembra el realm de prueba (roles, usuarios, clientes); otra vez tras cada up.sh
bash infra/validate-infra.sh   # un check por tecnología; sale != 0 si algo falla
```

Si la BD del stack lo permite, `infra/reset-db.sh` vacía los datos (esquema intacto):
se ejecuta antes de cada flujo `FL-*` de la validación funcional, cuyos Given asumen BD limpia.

El contenedor `order-devtools` trae solo las CLIs del stack elegido; sondéalas a mano con
`docker exec order-devtools <cli> ...` (p. ej. `psql`, `redis-cli`, `kcat`, `mc`, `aws`).

## Pruebas manuales en contenedor

Cuando el flujo de generación termina en verde, `deploy/` levanta el servicio **y** su
infraestructura en contenedores, para probarlo a mano con Postman, con curl o con un front.
Funciona igual con Docker que con Podman: el runtime se detecta solo, y se fuerza con
`CONTAINER_RUNTIME=podman` si tienes los dos.

```bash
bash deploy/up.sh      # construye la imagen, levanta todo y espera a que responda
bash deploy/down.sh    # apaga (con -v borra además los datos)
```

| URL | Qué es |
|---|---|
| <http://localhost:8080> | API |
| <http://localhost:8080/swagger-ui/index.html> | Swagger UI |
| <http://localhost:8080/actuator/health> | Health |
| <http://localhost:15672> | RabbitMQ (guest/guest) |
| <http://localhost:8180> | Keycloak (admin/admin) |
| <http://localhost:3001> | Grafana (telemetría) |
| <http://localhost:3001/d/keel-order> | Panel del servicio |
| <http://localhost:3001/alerting/list> | Alertas |
| <http://localhost:8091/__admin/requests> | Alertas recibidas (sumidero) |

Los puertos publicados salen de `deploy/.env`: si ya tienes algo ocupando el 8080 o el 5432,
cámbialo ahí y vuelve a ejecutar `up.sh`.

Para Postman, importa `deploy/postman/order-local.postman_environment.json` junto con las colecciones de `docs/postman/`,
selecciona el environment y ejecuta primero la colección de auth: guarda los tokens que usa la de negocio.
El environment trae la URL del token, los clientes y las credenciales de prueba; si cambias `APP_PORT` o el
puerto del proveedor de identidad en `.env`, cambia también `baseUrl`/`tokenUrl` en él.

El realm `order` se importa al arrancar Keycloak con los clientes máquina del diseño y sus
secretos. El diseño no declara roles, así que no hay usuarios de prueba: los tokens son de cliente
(`grant_type=client_credentials`). No hay que ejecutar nada para provisionarlo.

El contenedor corre con el perfil `develop`, que aplica las migraciones de `db/migration/` y deja a
Hibernate solo validando el esquema. Por eso **necesita el baseline**: hasta que exista
`src/main/resources/db/migration/V1__baseline_schema.sql` —lo produce el pase de calidad del flujo de
generación— la app arrancará y morirá diciendo que el esquema no valida. Es lo correcto: preferimos
eso a que un despliegue arranque contra un esquema que nadie ha declarado.

`deploy/` y `infra/` no son lo mismo y no conviene tenerlos arriba a la vez (publican los mismos
puertos): `infra/` es la infraestructura **de la generación**, con el toolbox `order-devtools` y
sus scripts de sondeo y reset, y ahí la app corre fuera del contenedor. `deploy/` es el servicio ya
empaquetado, para una persona.

## Perfiles y ambientes

El perfil activo se elige con la env var `PROFILE` (default `local`): `local`, `develop`, `production` y `test`.
Cada `application-<perfil>.yaml` importa sus fragmentos de `src/main/resources/parameters/<perfil>/`,
con gradiente de externalización: local usa valores literales (los del docker-compose), develop env vars
con default (`${VAR:default}`) y production env vars obligatorias sin default (`${VAR}`).

```bash
PROFILE=production DB_URL=... DB_USERNAME=... DB_PASSWORD=... java -jar build/libs/*.jar
```

Hay además dos perfiles auxiliares, finos y aditivos, que se activan **sobre** otro
(`PROFILE=local,<perfil>`) y sirven para trabajar el esquema: `schema-export` (Hibernate
escribe el DDL de las entidades a `build/schema/baseline.sql` sin tocar la BD; lo usa
`infra/export-schema.sh`) y `migrations` (Flyway aplica `db/migration/` y Hibernate solo
valida, como en los ambientes desplegados).

## Despliegue en producción

Pasos para levantar el servicio con el perfil `production` (esquema gobernado por las migraciones Flyway de `src/main/resources/db/migration/` — `ddl-auto: validate`, Hibernate no crea ni altera tablas —, Swagger UI deshabilitado y logs `root` en `WARN`):

1. Construye el artefacto: `./gradlew build -x test` (produce `build/libs/*.jar`).
2. Comprueba que `src/main/resources/db/migration/` contiene el baseline (`V1__baseline_schema.sql`) y las migraciones posteriores: Flyway las aplica al arrancar y registra cada una en `flyway_schema_history`. Si tu ambiente exige aplicarlas en un paso previo al despliegue, arranca una única instancia con `FLYWAY_ENABLED=true` y el resto con `FLYWAY_ENABLED=false`, o ejecuta Flyway por CLI contra la misma carpeta.
3. **Prueba el baseline sobre una BD sin esquema — este paso es tuyo y el pipeline no lo ejecuta.** El pase de calidad entrega el baseline exportado de las entidades y verificado en estático (`baselineTested: PENDING`), pero solo está *probado* si ha creado el esquema desde cero: contra una BD que Hibernate ya pobló con `ddl-auto: update`, el `validate` pasaría sin ejercitar la migración. Con la infraestructura local:

   ```bash
   bash infra/down.sh --volumes               # borra el volumen: BD sin esquema
   bash infra/up.sh
   PROFILE=local,migrations ./gradlew bootRun # Flyway crea, Hibernate valida
   ```

   Arranque limpio = baseline correcto. Un fallo de `validate` aquí es exactamente el que tendrías en producción, y dice qué columna o tipo no cuadra: corrige el SQL y repite.
4. Exporta las variables de entorno obligatorias de la tabla de abajo (secretos y endpoints reales del ambiente; en production ninguna trae valor por defecto).
5. Arranca el servicio: `PROFILE=production java -jar build/libs/*.jar`.
6. Verifica el arranque en los logs y contra el endpoint del servicio antes de darle tráfico (Swagger UI está deshabilitado en production).

### Parámetros obligatorios

| Variable | Para qué |
|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Parámetro declarado en `parameters/production/telemetry.yaml`. |
| `DB_URL` | URL JDBC de la base de datos. |
| `DB_USERNAME` | Usuario de la base de datos. |
| `DB_PASSWORD` | Contraseña de la base de datos. |
| `RABBITMQ_HOST` | Host de RabbitMQ. |
| `RABBITMQ_PORT` | Puerto de RabbitMQ. |
| `RABBITMQ_USERNAME` | Usuario de RabbitMQ. |
| `RABBITMQ_PASSWORD` | Contraseña de RabbitMQ. |
| `OAUTH2_ISSUER_URI` | Issuer del resource server OAuth2/OIDC que valida los tokens. |
| `SECURITY_CORS_ALLOWED_ORIGINS` | Orígenes permitidos por CORS (separados por coma). |
| `PAYMENT_BASE_URL` | URL base de un cliente HTTP declarado en el diseño. |
| `PAYMENT_CLIENT_ID` | client-id OAuth2 (client_credentials) de un cliente HTTP saliente. |
| `PAYMENT_CLIENT_SECRET` | client-secret OAuth2 de un cliente HTTP saliente. |
| `PAYMENT_TOKEN_URL` | token-uri del proveedor OAuth2 de un cliente HTTP saliente. |

Además hay parámetros operativos con valor por defecto (no obligatorios): `SERVER_PORT`, `SHUTDOWN_TIMEOUT`, `PROFILE`, `LOG_LEVEL_ROOT`, `LOG_LEVEL_APP`, `LOG_FORMAT`, `DEPLOYMENT_ENVIRONMENT`, `SERVICE_INSTANCE_ID`, `MANAGEMENT_PORT`, `MANAGEMENT_ENDPOINTS`, `MANAGEMENT_HEALTH_SHOW_DETAILS`, `TRACING_SAMPLING_PROBABILITY`, `TELEMETRY_EXPORT_ENABLED`, `LOG_EXPORT_OTLP`, `METRICS_EXPORT_STEP`, `METRICS_OTLP_HISTOGRAM_FLAVOR`, `METRICS_EXPORT_OTLP`, `METRICS_EXPORT_PROMETHEUS`, `DB_CONNECTION_FETCH`, `DB_POOL_MAX_SIZE`, `DB_POOL_CONNECTION_TIMEOUT_MS`, `DB_POOL_MAX_LIFETIME_MS`, `DB_TRANSACTION_TIMEOUT`, `FLYWAY_ENABLED`, `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`, `RABBITMQ_LISTENER_RECOVERY_INTERVAL_MS`, `MESSAGING_DESTINATION`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_AUTHORIZED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_AUTHORIZED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_ACTION_REQUIRED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_ACTION_REQUIRED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_CAPTURED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_CAPTURED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_FAILED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_FAILED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_CANCELED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_CANCELED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_ACTION_REJECTED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PAYMENT_ACTION_REJECTED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PRODUCT_UPSERTED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PRODUCT_UPSERTED_QUEUE`, `MESSAGING_SUBSCRIPTIONS_PRODUCT_RETIRED_TOPIC`, `MESSAGING_SUBSCRIPTIONS_PRODUCT_RETIRED_QUEUE`, `PROCESSED_EVENT_PURGE_CRON`, `PROCESSED_EVENT_PURGE_RETENTION_DAYS`, `OUTBOX_RELAY_POLL_MS`, `OUTBOX_RELAY_BATCH_SIZE`, `OUTBOX_RELAY_MAX_ATTEMPTS`, `OUTBOX_RELAY_BACKOFF_INITIAL_MS`, `OUTBOX_RELAY_BACKOFF_MAX_MS`, `OUTBOX_RELAY_CLAIM_TIMEOUT_MS`, `OUTBOX_PURGE_CRON`, `OUTBOX_PURGE_RETENTION_DAYS`, `IDEMPOTENCY_PURGE_CRON`, `OUTBOUND_DENIED_ADDRESSES`, `RECONCILIATION_CHARGE_ORDER_UNANSWERED_AFTER_SECONDS`, `RECONCILIATION_CHARGE_ORDER_CLAIM_TIMEOUT_MS`, `RECONCILIATION_CHARGE_ORDER_BATCH_SIZE`, `RECONCILIATION_CAPTURE_ORDER_PAYMENT_UNANSWERED_AFTER_SECONDS`, `RECONCILIATION_CAPTURE_ORDER_PAYMENT_CLAIM_TIMEOUT_MS`, `RECONCILIATION_CAPTURE_ORDER_PAYMENT_BATCH_SIZE`, `RECONCILIATION_CANCEL_ORDER_PAYMENT_UNANSWERED_AFTER_SECONDS`, `RECONCILIATION_CANCEL_ORDER_PAYMENT_CLAIM_TIMEOUT_MS`, `RECONCILIATION_CANCEL_ORDER_PAYMENT_BATCH_SIZE`, `TIMER_MAX_SLEEP_MS`, `TIMER_BATCH_SIZE`, `TIMER_LEASE_MS`, `TIMER_MAX_ATTEMPTS`, `TIMER_RETRY_DELAY_MS`, `TIMER_DRAIN_BUDGET_MS`, `TIMER_RETENTION_DAYS`, `TIMER_PURGE_CRON`, `SCHEDULING_ENABLED`, `ORDER_ACTION_REQUIRED_TIMEOUT_HOURS`, `ORDER_PAYMENT_PENDING_TIMEOUT_MINUTES`, `ORDER_PAYMENT_ACTION_RETRY_MINUTES`. Solo defínelos si necesitas cambiar su default.

El flujo `/keel-generate-spring` revisa y completa esta guía antes del commit: si al
implementar los adaptadores del stack (publishers/listeners del broker, adaptador de
storage, auth saliente de los clientes HTTP) el agente introduce parámetros nuevos,
quedan reflejados aquí. Fuente de verdad: los fragmentos
`src/main/resources/parameters/production/*.yaml` — todo `${VAR}` sin default es obligatorio.

## Qué genera el scaffolding y qué completa el agente

El scaffolding (transversal al stack, re-ejecutable con `keel-spring build`) produce la arquitectura hexagonal + CQRS
del prototipo de referencia, en un único microservicio (sin paquete shared ni Spring Modulith): dominio puro
(`domain/aggregate|entity|valueobject|enums|errors|events` + puertos en `domain/repository`), capa application
(commands/queries con Bean Validation, handlers stub en `usecases/`, ResponseDtos y mappers), e infraestructura
(entidades `Jpa` con auditoría automática, adaptadores `RepositoryImpl` con mapeo explícito, `UseCaseMediator`
con la frontera transaccional (la capa application no importa Spring: `@ApplicationComponent` propia),
`@LogExceptions` con su aspecto, contratos `EventEnvelope`/`EventMetadata` con puertos `<Evento>Publisher` y stub,
controllers `V1` con springdoc y `ApiExceptionHandler`), más configuración por perfiles y la infraestructura de prueba en `infra/`.
El código que depende de la infraestructura elegida (publishers/listeners del broker, adaptador de storage)
lo escribe el agente siguiendo las skills por tecnología `keel-spring-<tech>` (instaladas solo las del stack de `keel-stack.json`).
El punto de entrada para el agente es el archivo de contexto del repo (`AGENTS.md`, que `CLAUDE.md` importa),
junto con `docs/keel/architecture.md` y `docs/keel/constitution.md`; el repo es autosuficiente: incluye el diseño
(snapshot en `specs/`), los contratos formales (snapshot en `docs/`), las conventions en `docs/keel/` y —sembrados
para Claude Code y para opencode— la skill del generador, sus agentes y las guías del stack elegido.

Swagger UI (local/develop): http://localhost:8080/swagger-ui.html — deshabilitado en production.

Pendiente para el agente (`/keel-generate-spring`):

- Implementar los `handle(...)` con `// TODO (agente)` en `application/usecases/` (reglas, precondiciones, errores).
- Proteger los invariantes marcados con `// TODO invariante` en `domain/aggregate/`.
- Traducir los escenarios `FL-*` de `specs/validation-scenarios.md` a pruebas de integración en `src/integrationTest/` (la base `AbstractFlowIT` ya está generada) y dejar `./gradlew integrationTest` al 100% en OK. Las pruebas unitarias son un proceso posterior, fuera de la generación.
- `persistence`: producir el baseline de migraciones (`bash infra/export-schema.sh` con las entidades ya finales), revisarlo, verificarlo con el doble check estático y commitearlo como `src/main/resources/db/migration/V1__baseline_schema.sql` (sin él, `production` no arranca: Hibernate solo valida). Probarlo en vivo sobre una BD sin esquema queda para ti, a mano: ver «Despliegue en producción».
- `security`: el `SecurityFilterChain` ya está generado; crea el realm en el Keycloak de prueba (http://localhost:8180, admin/admin).
- `messaging`: haz `raise(...)` de cada evento en el método de negocio del agregado (la traducción a evento de integración y la entrega ya están generadas) e implementa el envío al broker — `OutboxDispatcher` o `<Evento>Publisher` según la `reliability` — y los `<Evento>Listener` de las suscripciones, según la skill `keel-spring-<broker>`.
- `http-clients`: puerto + adaptador RestClient + mapper ACL ya generados; completa los `*Fallback` (y el tipado records/mapper solo en llamadas declaradas en prosa).
- `dependencies`: `<Entidad>Projector` y `<Entidad>Reader` ya generados por cada réplica; completa la hidratación del Reader (`onMiss: fetch`) o el resultado degradado (`onMiss: degrade`). Nunca llames al Projector desde el listener: el camino es listener → guard → mediator → handler → Projector.
