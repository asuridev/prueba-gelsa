create table change_history ("at" timestamp(6) with time zone not null, change_kind varchar(16) not null, id uuid not null, root_id varchar(64) not null, root_type varchar(100) not null, author varchar(255) not null, changes text not null, primary key (id));
create table change_history_field (entry_id uuid not null, top varchar(100) not null, path varchar(255) not null, primary key (entry_id, path));
create table idempotency_record (created_at timestamp(6) with time zone not null, expires_at timestamp(6) with time zone not null, operation_scope varchar(128) not null, signature varchar(128) not null, idempotency_key varchar(255) not null, resource_id varchar(255), primary key (operation_scope, idempotency_key));
create table keel_timer (attempts integer not null, claimed_until timestamp(6) with time zone, due_at timestamp(6) with time zone not null, parked_at timestamp(6) with time zone, claim_token uuid, entity_id uuid not null, id uuid not null, spec varchar(120), kind varchar(160) not null, primary key (id), constraint uq_keel_timer_kind_entity unique (kind, entity_id));
create table outbox_event (attempts integer not null, created_at timestamp(6) with time zone not null, next_attempt_at timestamp(6) with time zone, published_at timestamp(6) with time zone, id uuid not null, last_error varchar(1024), destination varchar(255) not null, event_type varchar(255) not null, payload text not null, routing_key varchar(255) not null, primary key (id));
create table payment_methods (card_exp_month integer not null, card_exp_year integer not null, expired boolean, is_default boolean, id uuid not null, wallet_id uuid not null, card_brand varchar(30) not null, card_last4 varchar(255) not null, created_by varchar(255) not null, gateway_method_ref varchar(255) not null, status varchar(255) check ((status in ('ACTIVE','REMOVED'))), updated_by varchar(255) not null, primary key (id));
create table payments (amount numeric(19,3) not null, refunded_amount numeric(38,2), action_required_since timestamp(6) with time zone, awaiting_since timestamp(6) with time zone, created_at timestamp(6) with time zone, lock_version bigint not null, updated_at timestamp(6) with time zone, id uuid not null, payment_method_id uuid, charge_request_id varchar(100) not null, customer_ref varchar(200) not null, cancel_origin varchar(255) check ((cancel_origin in ('AUTHORIZED','ACTION_REQUIRED'))), created_by varchar(255) not null, currency varchar(255) not null, customer_action text, failure_reason varchar(255) check ((failure_reason in ('DECLINED','INSUFFICIENT_FUNDS','EXPIRED_CARD','AUTHENTICATION_FAILED','FRAUD_SUSPECTED','INVALID_PAYMENT_METHOD','NOT_RECEIVED','PROCESSING_ERROR'))), gateway_payment_id varchar(255), status varchar(255) check ((status in ('PENDING','ACTION_REQUIRED','AUTHORIZED','CAPTURING','CANCELING','CAPTURED','REFUNDING','REFUNDED','CANCELED','FAILED'))), updated_by varchar(255) not null, primary key (id), constraint uk_payments_natural unique (charge_request_id));
create table refunds (amount numeric(19,3) not null, created_at timestamp(6) with time zone, updated_at timestamp(6) with time zone, id uuid not null, payment_id uuid not null, refund_request_id varchar(100) not null, reason varchar(200), created_by varchar(255) not null, status varchar(255) check ((status in ('PENDING','SUCCEEDED','FAILED'))), updated_by varchar(255) not null, primary key (id), constraint uk_refunds_natural unique (refund_request_id));
create table wallets (lock_version bigint not null, id uuid not null, customer_ref varchar(200) not null, created_by varchar(255) not null, updated_by varchar(255) not null, primary key (id), constraint uk_wallets_natural unique (customer_ref));
create index ix_change_history_root on change_history (root_type, root_id, "at", id);
create index ix_idempotency_record_expires_at on idempotency_record (expires_at);
create index ix_keel_timer_due_at on keel_timer (due_at);
create index ix_outbox_event_pending on outbox_event (published_at, created_at);
create index idx_payment_methods_wallet_status on payment_methods (wallet_id, status);
create index idx_payments_status_awaiting_since on payments (status, awaiting_since);
create index idx_payments_customer_ref_created_at on payments (customer_ref, created_at);
create index idx_refunds_payment_created_at on refunds (payment_id, created_at);
alter table if exists change_history_field add constraint fk_change_history_field_entry foreign key (entry_id) references change_history on delete cascade;
alter table if exists payment_methods add constraint fk_payment_methods_wallet foreign key (wallet_id) references wallets;
alter table if exists refunds add constraint fk_refunds_payment foreign key (payment_id) references payments;

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
