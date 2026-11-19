package com.ecomerce.payment.infrastructure.timer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * El cronómetro sobre PostgreSQL, con las sentencias de keel-core
 * ({@code timer-store.js}) TAL CUAL: son contrato con los otros generadores, que comparten la tabla, y las mide
 * {@code timer-sql-check} contra el motor. No las reescribas aquí.
 *
 * <p>Va por JDBC ({@link JdbcClient}) y no por JPA: las sentencias usan la hora de la base, {@code FOR UPDATE SKIP
 * LOCKED} y el upsert de cada motor, que JPQL no dice. Sobre el mismo {@code DataSource} que JPA, así que el armado
 * entra en la transacción del caso de uso ({@code JpaTransactionManager} expone su conexión a JDBC).
 */
@Component
public class JdbcTimerStore implements TimerStore {

    private static final String DB_NOW = "SELECT current_timestamp";
    private static final String ARM = "INSERT INTO keel_timer (id, kind, entity_id, spec, due_at, claimed_until, claim_token, attempts, parked_at) VALUES (:id, :kind, :entityId, :spec, :dueAt, NULL, NULL, 0, NULL) ON CONFLICT (kind, entity_id) DO UPDATE SET due_at = EXCLUDED.due_at, claimed_until = NULL, claim_token = NULL, attempts = 0, parked_at = NULL";
    private static final String ENSURE_RECURRING = "INSERT INTO keel_timer (id, kind, entity_id, spec, due_at, claimed_until, claim_token, attempts, parked_at) VALUES (:id, :kind, :entityId, :spec, :dueAt, NULL, NULL, 0, NULL) ON CONFLICT (kind, entity_id) DO UPDATE SET due_at = EXCLUDED.due_at, spec = EXCLUDED.spec WHERE keel_timer.spec IS DISTINCT FROM EXCLUDED.spec";
    private static final String CLAIM_LOCK = "SELECT id FROM keel_timer WHERE kind IN (:kinds) AND parked_at IS NULL AND due_at <= current_timestamp AND (claimed_until IS NULL OR claimed_until < current_timestamp) ORDER BY due_at LIMIT :limit FOR UPDATE SKIP LOCKED";
    private static final String CLAIM_MARK = "UPDATE keel_timer SET claimed_until = current_timestamp + (CAST(:leaseMs AS bigint) * interval '1 millisecond'), claim_token = :token WHERE id IN (:ids)";
    private static final String CLAIMED = "SELECT id, kind, entity_id, spec, due_at, claimed_until, claim_token, attempts, parked_at FROM keel_timer WHERE claim_token = :token ORDER BY due_at";
    private static final String COMPLETE = "DELETE FROM keel_timer WHERE id = :id AND claim_token = :token";
    private static final String RESCHEDULE = "UPDATE keel_timer SET due_at = :dueAt, claimed_until = NULL, claim_token = NULL, attempts = 0 WHERE id = :id AND claim_token = :token";
    private static final String FAIL = "UPDATE keel_timer SET parked_at = CASE WHEN attempts + 1 >= :maxAttempts THEN current_timestamp ELSE NULL END, attempts = attempts + 1, due_at = current_timestamp + (CAST(:retryDelayMs AS bigint) * interval '1 millisecond'), claimed_until = NULL, claim_token = NULL WHERE id = :id AND claim_token = :token";
    private static final String NEXT_DUE_MS = "SELECT CAST(EXTRACT(EPOCH FROM (min(CASE WHEN claimed_until IS NOT NULL AND claimed_until > due_at THEN claimed_until ELSE due_at END) - current_timestamp)) * 1000 AS bigint) FROM keel_timer WHERE kind IN (:kinds) AND parked_at IS NULL";
    private static final String PARKED_COUNT = "SELECT count(*) FROM keel_timer WHERE parked_at IS NOT NULL";
    private static final String LAG_MS = "SELECT CAST(EXTRACT(EPOCH FROM (current_timestamp - min(due_at))) * 1000 AS bigint) FROM keel_timer WHERE kind IN (:kinds) AND parked_at IS NULL AND due_at <= current_timestamp AND (claimed_until IS NULL OR claimed_until < current_timestamp)";
    private static final String PURGE = "DELETE FROM keel_timer WHERE due_at < current_timestamp - (CAST(:retentionDays AS integer) * interval '1 day') AND claim_token IS NULL";

    private final JdbcClient jdbc;
    private final TransactionTemplate ownTransaction;
    private final ApplicationEventPublisher events;

    public JdbcTimerStore(DataSource dataSource, PlatformTransactionManager transactionManager, ApplicationEventPublisher events) {
        this.jdbc = JdbcClient.create(dataSource);
        // El reclamo son tres sentencias en UNA transacción PROPIA: confirma antes de volver, así que las demás réplicas
        // dejan de ver el lote aunque quien llama tarde en procesarlo.
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.events = events;
    }

    @Override
    public Instant dbNow() {
        return jdbc.sql(DB_NOW).query((rs, row) -> readTime(rs, 1)).single();
    }

    @Override
    public void arm(String kind, UUID entityId, Instant dueAt) {
        jdbc.sql(ARM)
                .param("id", uuid(UUID.randomUUID()))
                .param("kind", kind)
                .param("entityId", uuid(entityId))
                .param("spec", null)
                .param("dueAt", time(dueAt))
                .update();
        events.publishEvent(new TimerArmed(dueAt));
    }

    @Override
    public void ensureRecurring(String kind, String spec, Instant dueAt) {
        jdbc.sql(ENSURE_RECURRING)
                .param("id", uuid(UUID.randomUUID()))
                .param("kind", kind)
                .param("entityId", uuid(UUID.fromString(TimerKinds.NIL_ENTITY_ID)))
                .param("spec", spec)
                .param("dueAt", time(dueAt))
                .update();
    }

    @Override
    public List<ClaimedTimer> claim(List<String> kinds, int limit, long leaseMs, UUID token) {
        return ownTransaction.execute(status -> {
            List<Object> ids = jdbc.sql(CLAIM_LOCK)
                    .param("kinds", kinds)
                    .param("limit", limit)
                    .query((rs, row) -> uuid(readUuid(rs, "id")))
                    .list();
            if (ids.isEmpty()) {
                return List.of();
            }
            jdbc.sql(CLAIM_MARK).param("leaseMs", leaseMs).param("token", uuid(token)).param("ids", ids).update();
            return jdbc.sql(CLAIMED)
                    .param("token", uuid(token))
                    .query((rs, row) -> new ClaimedTimer(readUuid(rs, "id").toString(), rs.getString("kind"),
                            readUuid(rs, "entity_id"), readTime(rs, "due_at"), rs.getInt("attempts")))
                    .list();
        });
    }

    @Override
    public boolean complete(String id, UUID token) {
        return jdbc.sql(COMPLETE).param("id", uuid(UUID.fromString(id))).param("token", uuid(token)).update() > 0;
    }

    @Override
    public boolean reschedule(String id, UUID token, Instant dueAt) {
        return jdbc.sql(RESCHEDULE)
                .param("dueAt", time(dueAt))
                .param("id", uuid(UUID.fromString(id)))
                .param("token", uuid(token))
                .update() > 0;
    }

    @Override
    public boolean fail(String id, UUID token, int maxAttempts, long retryDelayMs) {
        return jdbc.sql(FAIL)
                .param("maxAttempts", maxAttempts)
                .param("retryDelayMs", retryDelayMs)
                .param("id", uuid(UUID.fromString(id)))
                .param("token", uuid(token))
                .update() > 0;
    }

    @Override
    public Long nextDueMs(List<String> kinds) {
        return jdbc.sql(NEXT_DUE_MS).param("kinds", kinds).query((rs, row) -> {
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        }).optional().orElse(null);
    }

    @Override
    public long parkedCount() {
        return jdbc.sql(PARKED_COUNT).query(Long.class).single();
    }

    @Override
    public Long lagMs(List<String> kinds) {
        return jdbc.sql(LAG_MS).param("kinds", kinds).query((rs, row) -> {
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        }).optional().orElse(null);
    }

    @Override
    public int purge(int retentionDays) {
        return jdbc.sql(PURGE).param("retentionDays", retentionDays).update();
    }

    // PostgreSQL: uuid nativo y timestamp with time zone.
    private static Object uuid(UUID value) {
        return value;
    }

    private static UUID readUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Object time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant readTime(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Instant readTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
