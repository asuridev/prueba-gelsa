# notifications-spring — contexto para el agente

Envía correo transaccional a partir de plantillas que gestiona negocio, a petición de los sistemas de la organización por HTTP o por eventos, sin enviar nunca dos veces el mismo correo.

> **Quién eres.** Si acabas de invocar `/keel-generate-spring`, eres el **orquestador**: tu proceso está en la
> skill, y este documento es el contexto que pasas a los agentes. Si eres uno de los agentes de
> `.claude/agents/`, manda **tu propio archivo de agente**: aquí tienes el mapa del repo (diseño,
> stack, arquitectura, orden de las capas), no tu lista de tareas — tu alcance y tu criterio de
> terminado son los de tu agente, y **tú no lanzas agentes**.

Proyecto generado desde el diseño Keel `notifications` v0.3.0 por keel-spring 0.4.0 (scaffolding transversal al stack: el proyecto compila y arranca). Lo que queda pendiente es lo que depende de la infraestructura elegida o del negocio: implementaciones de puertos de infraestructura, lógica de negocio e invariantes (repartido entre los agentes; ver § Quién ejecuta esto). **Sin pruebas unitarias**: no las escribas ni ejecutes `./gradlew test` — la suite es un proceso independiente y posterior a que el diseñador valide el servidor; el andamiaje de test que ya está (deps, perfil `test` con H2, `NotificationsApplicationTests`) se deja intacto para esa fase. Los escenarios `FL-*` sí se traducen a pruebas de integración en `src/integrationTest/` (caja negra contra el contrato, source set aparte). El criterio de terminado **del pipeline** es la compilación en verde más `./gradlew integrationTest` con el **100%** de los escenarios de `specs/validation-scenarios.md` en OK. Este repo es **autosuficiente**: diseño, skill, convenciones y guías del stack van incluidos. Localiza los puntos de trabajo con `grep -rn "TODO" src`.

## Fuente de verdad del diseño

Los artefactos Keel están en `specs/` (snapshot que `keel-spring build` refresca en cada ejecución). Si trabajas dentro
del workspace Keel que generó este proyecto, el canónico es `../../specs/notifications/`: ante cualquier duda o cambio
de diseño, manda el del workspace y se re-ejecuta el build.

| Artefacto | Contenido |
|---|---|
| `specs/service.keel.yaml` | manifiesto: nombre, versión, DSL y capas declaradas |
| `specs/domain.keel.yaml` | entidades, agregados, tipos, invariantes y lifecycle |
| `specs/use-cases.keel.yaml` | operaciones (commands/queries), precondiciones, reglas y errores |
| `specs/api.keel.yaml` | endpoints, basePath y paginación |
| `specs/security.keel.yaml` | autenticación, roles, permisos y reglas de acceso |
| `specs/messaging.keel.yaml` | eventos publicados y suscripciones |
| `specs/persistence.keel.yaml` | mapeo a persistencia, claves naturales e índices |
| `specs/validation-scenarios.md` | escenarios FL-* Given/When/Then que cierran el diseño |

Reglas inviolables completas en `docs/keel/constitution.md`. Si un escenario contradice el spec, el hueco es del
diseño: proponlo como cambio a los artefactos, no lo acomodes en el código. Todo identificador nuevo (paquetes,
directorios, archivos, clases, métodos, variables, tablas) va en inglés; comentarios y docs en español.

## Arquitectura

`docs/keel/architecture.md` describe la arquitectura hexagonal + CQRS del proyecto y la función de cada paquete
(`domain`, `application`, `infrastructure`). `docs/keel/constitution.md` recoge las reglas que esa arquitectura
nunca puede romper (frontera hexagonal, transaccionalidad, contratos públicos, precisión numérica). Léelos antes de tocar código
si no conoces ya la estructura.

## Stack elegido

`keel-stack.json`: com.ecomerce · PostgreSQL + RabbitMQ + Keycloak (contenedor de prueba) + OpenTelemetry (trazas, métricas y logs por OTLP a un colector). Respétalo; para cambiarlo, borra ese archivo y re-ejecuta
`keel-spring build --force` desde la raíz del workspace.

## Conocimiento local

La skill `/keel-generate-spring` de este proyecto arranca el proceso; `docs/keel/conventions/` trae las convenciones que consultan los agentes de `.claude/agents/`, y las guías del stack
están instaladas como skills propias por tecnología (solo las del stack elegido):

- `docs/keel/conventions/mapping.md` — mapeo DSL Keel → código Spring, capa por capa. Síguelo estrictamente.
- `docs/keel/conventions/project-layout.md` — estructura del proyecto y sus paquetes.
- `docs/keel/conventions/domain-modeling.md` — cómo se modela el dominio: agregados ricos, invariantes, value objects y
  reparto de la validación entre capas.
- `docs/keel/conventions/infra-validation.md` — sondeo por tecnología de la infraestructura de prueba.
- `docs/keel/conventions/integration-tests.md` — cómo se traducen los escenarios `FL-*` a pruebas de integración.
- `.claude/skills/keel-spring-database/SKILL.md` — tuning de datasource/Hikari, particularidades del dialecto y validación de la BD (el código JPA ya lo genera build): qué dejó listo build y qué te toca a ti; sus `references/` (configuración, implementación, troubleshooting) se leen bajo demanda.
- `.claude/skills/keel-spring-rabbitmq/SKILL.md` — broker RabbitMQ: qué dejó listo build y qué te toca a ti; sus `references/` (configuración, implementación, troubleshooting) se leen bajo demanda.
- `.claude/skills/keel-spring-mail/SKILL.md` — keel-spring-mail: qué dejó listo build y qué te toca a ti; sus `references/` (configuración, implementación, troubleshooting) se leen bajo demanda.
- `.claude/skills/keel-spring-keycloak/SKILL.md` — auth Keycloak: qué dejó listo build y qué te toca a ti; sus `references/` (configuración, implementación, troubleshooting) se leen bajo demanda.

## Orden de trabajo: completar el scaffolding, capa por capa

1. **application** (`specs/use-cases.keel.yaml`): implementa cada `handle(...)` marcado `// TODO (agente)` en `application/usecases/`. Usa el **puerto** de `domain/repository` y el `ApplicationMapper`, nunca las clases `Jpa`. Los handlers llevan `@ApplicationComponent` (no añadas `@Component` ni `@Transactional`: la transacción la abre el `UseCaseMediator`). Implementa `preconditions` y `rules` en el orden del artefacto lanzando los errores de `domain/errors`.
2. **domain** (`specs/domain.keel.yaml`): siguiendo `docs/keel/conventions/domain-modeling.md`, escribe el factory de creación, los métodos semánticos de cada transición del `lifecycle` y la guarda de cada `// TODO invariante` en `domain/aggregate`; deriva los campos `computed` marcados `// TODO computed`. El agregado sale sin setters: la mutación va por métodos de negocio. El dominio es puro: nada de JPA aquí.
3. **api** (`specs/api.keel.yaml`): controllers, DTOs y `ApiExceptionHandler` ya generados. Atiende las rutas marcadas `// TODO: revisar ruta` y, al cerrar el código, cruza cada endpoint con su firma: un `@PathVariable` por cada `{segmento}`, cuerpo solo en POST/PUT/PATCH y el `successStatus` declarado. Un desajuste es defecto del scaffolding: repórtalo, no lo compenses cambiando el contrato.
4. **security** (`specs/security.keel.yaml`): ya generado (`SecurityFilterChain`, resource server o filtro api-key, `JwtAuthConverter`). No lo reescribas; interviene solo si el diseño exige lógica que el mapeo de claims no cubre.
5. **messaging** (`specs/messaging.keel.yaml`): los eventos los emite el **agregado** con `raise(<Evento>Event.of(...))` en su método de negocio (build dejó el buffer y un TODO por evento); el adaptador de repositorio los drena al persistir y el `<Servicio>DomainEventBridge` los traduce a evento de integración y los entrega. Tú, siguiendo la skill `.claude/skills/keel-spring-rabbitmq/SKILL.md`, implementas solo el envío al broker (`OutboxDispatcher` con `reliability: outbox`, `<Evento>Publisher` con `best-effort` — sustituye y elimina su stub), la configuración del broker si aplica y el `<Evento>Listener` por suscripción (binding, política `onFailure`, apertura de la correlación con `CorrelationContext.runWith(envelope.metadata(), ...)` —la sobrecarga de la metadata, no la del `correlationId`—, deduplicación con el `IdempotencyGuard` ya generado y despacho vía `UseCaseMediator`).
6. **telemetría** (`telemetry: otel` en `keel-stack.json`): trazas, métricas y logs por OTLP a un colector, **ya generados enteros** por build (`infrastructure/telemetry`, el mediator, el relay y los clientes HTTP). No abras spans a mano ni metas datos personales en atributos o logs; la única pieza tuya es abrir el contexto del listener con la metadata. Todo en `docs/keel/conventions/observability.md`.
7. **persistence** (`specs/persistence.keel.yaml`): entidades `Jpa`, puertos y adaptadores ya generados; respeta `consistency.transactionalBoundary` en los handlers. El esquema de los ambientes desplegados lo gobiernan las migraciones de `src/main/resources/db/migration/` (production usa `ddl-auto: validate`): el baseline se exporta de las entidades **ya finales** con `bash infra/export-schema.sh` en el cierre, no al empezar. Para migraciones, tuning del datasource/Hikari y particularidades del dialecto, la skill `.claude/skills/keel-spring-database/SKILL.md`.
8. **Configuración por ambiente**: cualquier configuración nueva va en `src/main/resources/parameters/<perfil>/` respetando el gradiente (local literal, develop `${VAR:default}`, production `${VAR}` sin default); nunca credenciales reales.

## Verificación — criterio de salida del pipeline

Lo que hace falta para dar por terminada **la generación**, repartido entre varios agentes. No es
el criterio de ninguno en particular: si eres un agente de `.claude/agents/`, el tuyo está en tu
archivo y suele ser más estrecho que esta lista.

1. `./gradlew build -x test` en verde: compilación y empaquetado (en Windows `gradlew.bat build -x test`).
2. Infraestructura de prueba (en `infra/`): `bash infra/up.sh` (con podman,
   `CONTAINER_RUNTIME=podman bash infra/up.sh`; el script resuelve además el frontend de compose) y `bash infra/validate-infra.sh` antes de ejercitar escenarios — un fallo de dependencia no debe confundirse con un bug.
3. Escenarios: `./gradlew integrationTest` — los flujos `FL-*` de `specs/validation-scenarios.md`, ya traducidos a
   pruebas de integración en `src/integrationTest/`, contra el servidor real (lo arranca JUnit) y esta infraestructura.
   Reporta la matriz escenario → resultado: la generación solo se da por terminada con el **100%** en OK.
4. Arranque del contexto: `./gradlew test` — en este punto la suite unitaria es solo `contextLoads()`, y es lo
   único que comprueba que **todos los beans arrancan bajo el perfil `test`** que genera `build`. No se escriben
   pruebas unitarias nuevas aquí: la suite unitaria es un proceso posterior.
5. Migraciones: con las entidades ya finales, `bash infra/export-schema.sh`, revisa el DDL y cópialo como
   `src/main/resources/db/migration/V1__baseline_schema.sql`; verifícalo con el **doble check estático** (`diff`
   contra el DDL exportado + contraste con las entidades `Jpa` y el diseño). Sin baseline el servicio no puede
   desplegarse: en `production` Hibernate no crea nada. La prueba en vivo —arrancar con `PROFILE=local,migrations`
   sobre una BD **sin esquema**— es una verificación **manual del diseñador**, posterior a la generación: exige
   borrar el volumen de la BD, que es la misma sobre la que corren los escenarios.

## Quién ejecuta esto

Lo reparte la skill `/keel-generate-spring`, **único orquestador** del pipeline: los agentes de `.claude/agents/` son
**hojas** y ninguno invoca a otro (su frontmatter no se lo permite). El reparto es: `keel-spring-code` (código, sin
tests) en paralelo con `keel-spring-infra` (infraestructura arriba y sana) y con `keel-spring-tests` (escenarios
`FL-*` → `src/integrationTest/`, en caja negra). Con los tres en OK, la skill ejecuta `bash infra/score-scenarios.sh`,
que corre la suite y compone la matriz desde el XML de JUnit: eso es determinista y no gasta un agente. Solo si la
matriz trae algo en rojo se invoca `keel-spring-validate`, que **solo arbitra** de quién es la culpa de cada fallo.
Al final, `keel-spring-quality` (pase de calidad no-conductual con la compilación en verde, más el baseline de migraciones del punto 4, y los escenarios al 100% como no-regresión propia). El pipeline completo —fases, gating y handoffs— está en
`docs/keel/orchestration.md`.

El cierre —commit en este repo (`Generado desde specs/notifications v0.3.0`) y el `README.md`
con las decisiones tomadas y los huecos del diseño detectados— lo hace **quien orquesta**, con todo lo anterior en verde.
