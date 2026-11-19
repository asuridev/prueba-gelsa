-- Índices únicos condicionados, derivados de specs/persistence.keel.yaml.
-- Generado por keel-spring build: NO se edita a mano (se regenera en cada build).
--
-- Hibernate no puede inferirlos de las entidades porque JPA no tiene predicado en
-- @Index, así que este archivo es su única fuente. Lo consumen dos caminos:
--   * los perfiles con ddl-auto (local, test), vía spring.sql.init;
--   * el baseline de migraciones, al que infra/export-schema.sh lo añade.
-- Por eso cada sentencia es idempotente: se ejecuta en cada arranque.
--
-- CONTRATO DE ORDEN. Un índice único parcial se comprueba por FILA y NO se puede diferir
-- (DEFERRABLE es de constraints, y una constraint única parcial no existe). Así que una
-- operación que RELEVA —saca una fila del estado condicionado y mete otra en el mismo acto—
-- tiene que confirmar la salida ANTES de la entrada, o habrá un instante con dos filas en ese
-- estado y la transición LEGÍTIMA morirá con este mismo error de unicidad.
--
-- Aquí releva: activateTemplateVersion. El puerto del agregado trae flushPendingWrites() para eso, y el handler
-- lo llama ENTRE las dos escrituras. Lo verifica infra/check-idempotency.sh.

-- EmailTemplateVersion: como máximo una fila por (emailTemplate) con status = active (almacenado como 'ACTIVE').
CREATE UNIQUE INDEX IF NOT EXISTS uk_email_template_versions_email_template ON email_template_versions (email_template_id) WHERE status = 'ACTIVE';
