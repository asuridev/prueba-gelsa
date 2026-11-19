# Migraciones de esquema (Flyway)

Cada archivo `.sql` de este directorio es una migración versionada que Flyway
aplica **en orden** al arrancar el servicio, y que queda registrada en la tabla
`flyway_schema_history`. Es la fuente de verdad del esquema en `develop` y
`production`, donde Hibernate solo valida (`ddl-auto: validate`).

## Convención de nombres

| Patrón | Para qué |
|---|---|
| `V<n>__<snake_case>.sql` | Migración versionada; se aplica una vez. `V1__baseline_schema.sql`, `V2__add_product_sku_index.sql`. |
| `R__<snake_case>.sql` | Repeatable: se reaplica cuando cambia su contenido. Solo para datos de referencia idempotentes o vistas. |

## El baseline (V1)

No se escribe a mano: se **exporta** de las entidades JPA ya finales, para que el
esquema y el mapeo no puedan divergir.

```bash
bash infra/up.sh                    # el export necesita la BD arriba
bash infra/export-schema.sh                         # → build/schema/baseline.sql
```

Revisa el SQL exportado (nombres de constraint e índices, tipos del dialecto) y
cópialo como `V1__baseline_schema.sql`. Eso lo produce el **pase de calidad** del
flujo de generación, que además lo verifica en estático: `diff` contra el DDL
exportado y contraste con las entidades `Jpa` y el diseño.

**La prueba en vivo es tuya**, antes del primer despliegue: el baseline solo está
probado si ha creado el esquema **desde cero** —contra una BD que Hibernate ya
pobló con `ddl-auto: update`, el `validate` pasaría sin ejercitar nada—.

```bash
bash infra/down.sh --volumes               # borra el volumen: BD sin esquema
bash infra/up.sh
PROFILE=local,migrations ./gradlew bootRun # Flyway crea, Hibernate valida
```

El pipeline no la ejecuta a propósito: borrar el volumen destruiría la base de datos
sobre la que corren los escenarios `FL-*`. El procedimiento completo y su checklist
están en `references/migrations.md` de la skill `keel-spring-database`.

## Reglas duras

- **Nunca edites una migración ya aplicada** en cualquier ambiente: Flyway guarda
  su checksum y el arranque fallará. Los cambios van en una `V<n+1>` nueva.
- **Los `V<n>__keel_*.sql` los escribe build**: crean las tablas del generador
  (outbox, idempotencia, reconciliación, cronómetro) que una base ya desplegada no
  tenía. No los edites ni los renumeres, y no repitas sus tablas en un paso tuyo:
  el tuyo va después, con el número siguiente.
- **Nunca `flyway clean`**: borra el esquema. Está deshabilitado en `production`.
- El esquema que describan estas migraciones debe respetar el snapshot del diseño
  en `specs/` (claves naturales e índices de `persistence.keel.yaml`); el diseño manda.
