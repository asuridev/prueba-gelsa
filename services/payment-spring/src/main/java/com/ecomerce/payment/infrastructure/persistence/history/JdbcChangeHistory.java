package com.ecomerce.payment.infrastructure.persistence.history;

import com.ecomerce.payment.domain.history.ChangeHistory;
import com.ecomerce.payment.domain.history.FieldChange;
import com.ecomerce.payment.domain.history.HistoryAnonymization;
import com.ecomerce.payment.domain.history.HistoryDiff;
import com.ecomerce.payment.domain.history.HistoryEntry;
import com.ecomerce.payment.domain.history.HistoryFilter;
import com.ecomerce.payment.domain.history.PersonalPath;
import com.ecomerce.payment.domain.identity.Uuids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

/**
 * El historial de cambios sobre PostgreSQL, con las sentencias de keel-core ({@code history-store.js})
 * TAL CUAL: son contrato con los otros generadores, que comparten las tablas, y las mide {@code timer-sql-check} contra el
 * motor. No las reescribas aquí.
 *
 * <p>Va por JDBC sobre el mismo {@code DataSource} que JPA, así que la entrada entra en la transacción de la escritura que
 * la origina ({@code JpaTransactionManager} expone su conexión a JDBC): si la escritura revierte, la entrada también (D1).
 * La hora es la del motor; a igual hora desempata el id, un UUID versión 7.
 */
@Component
public class JdbcChangeHistory implements ChangeHistory {

    private static final String INSERT_ENTRY = "INSERT INTO change_history (id, root_type, root_id, at, author, change_kind, changes) VALUES (:id, :rootType, :rootId, date_trunc('milliseconds', current_timestamp), :author, :change, :changes)";
    private static final String INSERT_FIELD = "INSERT INTO change_history_field (entry_id, path, top) VALUES (:entryId, :path, :top)";
    private static final String PAGE_HEAD = "SELECT h.id, h.at, h.author, h.change_kind, h.changes FROM change_history h WHERE h.root_type = :rootType AND h.root_id = :rootId";
    private static final String COUNT_HEAD = "SELECT count(*) FROM change_history h WHERE h.root_type = :rootType AND h.root_id = :rootId";
    private static final String FILTER_FIELD = " AND EXISTS (SELECT 1 FROM change_history_field f WHERE f.entry_id = h.id AND (f.path = :field OR f.top = :field))";
    private static final String FILTER_FROM = " AND h.at >= :from";
    private static final String FILTER_TO = " AND h.at <= :to";
    private static final String PAGE_TAIL = " ORDER BY h.at DESC, h.id DESC LIMIT :limit OFFSET :offset";
    private static final String ENTRIES_OF_ROOT = "SELECT id, changes FROM change_history WHERE root_type = :rootType AND root_id = :rootId";
    private static final String UPDATE_CHANGES = "UPDATE change_history SET changes = :changes WHERE id = :id";
    private static final String DELETE_FIELDS = "DELETE FROM change_history_field WHERE entry_id IN (SELECT id FROM change_history WHERE root_type = :rootType AND root_id = :rootId)";
    private static final String DELETE_ENTRIES = "DELETE FROM change_history WHERE root_type = :rootType AND root_id = :rootId";

    private static final TypeReference<List<FieldChange>> CHANGES = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AuditorAware<String> auditor;

    public JdbcChangeHistory(DataSource dataSource, ObjectMapper mapper, AuditorAware<String> auditor) {
        this.jdbc = JdbcClient.create(dataSource);
        this.mapper = mapper;
        this.auditor = auditor;
    }

    @Override
    public void record(String rootType, String rootId, String change, List<FieldChange> changes) {
        UUID id = Uuids.v7();
        jdbc.sql(INSERT_ENTRY)
                .param("id", uuid(id))
                .param("rootType", rootType)
                .param("rootId", rootId)
                .param("author", auditor.getCurrentAuditor().orElse("system"))
                .param("change", change)
                .param("changes", mapper.writeValueAsString(changes))
                .update();
        for (Map.Entry<String, String> row : HistoryDiff.fieldRows(changes).entrySet()) {
            jdbc.sql(INSERT_FIELD).param("entryId", uuid(id)).param("path", row.getKey()).param("top", row.getValue()).update();
        }
    }

    @Override
    public Page<HistoryEntry> page(String rootType, String rootId, HistoryFilter filter, Pageable pageable) {
        // Los filtros presentes, en el orden de keel-core (historyPageSql): uno que no llega no se escribe.
        String filters = (filter.field() != null ? FILTER_FIELD : "") + (filter.from() != null ? FILTER_FROM : "") + (filter.to() != null ? FILTER_TO : "");
        JdbcClient.StatementSpec rows = bind(jdbc.sql(PAGE_HEAD + filters + PAGE_TAIL), rootType, rootId, filter)
                .param("limit", pageable.getPageSize())
                .param("offset", pageable.getOffset());
        List<HistoryEntry> entries = rows
                .query((rs, row) -> new HistoryEntry(readTime(rs, "at"), rs.getString("author"), rs.getString("change_kind"), read(rs.getString("changes"))))
                .list();
        long total = bind(jdbc.sql(COUNT_HEAD + filters), rootType, rootId, filter).query(Long.class).single();
        return new PageImpl<>(entries, pageable, total);
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, String rootType, String rootId, HistoryFilter filter) {
        JdbcClient.StatementSpec bound = spec.param("rootType", rootType).param("rootId", rootId);
        if (filter.field() != null) {
            bound = bound.param("field", filter.field());
        }
        if (filter.from() != null) {
            bound = bound.param("from", time(filter.from()));
        }
        if (filter.to() != null) {
            bound = bound.param("to", time(filter.to()));
        }
        return bound;
    }

    @Override
    public void anonymize(String rootType, String rootId, List<PersonalPath> personal) {
        List<List<FieldChange>> all = new ArrayList<>();
        List<Map.Entry<UUID, List<FieldChange>>> stored = jdbc.sql(ENTRIES_OF_ROOT)
                .param("rootType", rootType)
                .param("rootId", rootId)
                .query((rs, row) -> Map.entry(readUuid(rs, "id"), read(rs.getString("changes"))))
                .list();
        for (Map.Entry<UUID, List<FieldChange>> entry : stored) {
            HistoryAnonymization.Rewrite rewrite = HistoryAnonymization.rewrite(entry.getValue(), personal, rootId);
            all.add(rewrite.changes());
            if (rewrite.touched()) {
                jdbc.sql(UPDATE_CHANGES).param("changes", mapper.writeValueAsString(rewrite.changes())).param("id", uuid(entry.getKey())).update();
            }
        }
        record(rootType, rootId, ANONYMIZED, HistoryAnonymization.anonymizedEntry(all, personal, rootId));
    }

    @Override
    public void purge(String rootType, String rootId) {
        jdbc.sql(DELETE_FIELDS).param("rootType", rootType).param("rootId", rootId).update();
        jdbc.sql(DELETE_ENTRIES).param("rootType", rootType).param("rootId", rootId).update();
    }

    @Override
    public JsonNode value(Object value) {
        return value == null ? NullNode.getInstance() : mapper.valueToTree(value);
    }

    private List<FieldChange> read(String changes) {
        return mapper.readValue(changes, CHANGES);
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

    private static Instant readTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
