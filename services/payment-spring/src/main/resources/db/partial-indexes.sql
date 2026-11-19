-- Índices únicos condicionados, derivados de specs/persistence.keel.yaml.
-- Generado por keel-spring build: NO se edita a mano (se regenera en cada build).
--
-- Hibernate no puede inferirlos de las entidades porque JPA no tiene predicado en
-- @Index, así que este archivo es su única fuente. Lo consumen dos caminos:
--   * los perfiles con ddl-auto (local, test), vía spring.sql.init;
--   * el baseline de migraciones, al que infra/export-schema.sh lo añade.
-- Por eso cada sentencia es idempotente: se ejecuta en cada arranque.

-- PaymentMethod: como máximo una fila por (wallet) con isDefault = true.
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_methods_wallet ON payment_methods (wallet_id) WHERE is_default = TRUE;
