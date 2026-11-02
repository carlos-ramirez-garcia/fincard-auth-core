package com.fincard.core.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class EventOutboxDao {

    public record PendingEvent(long outboxId, String transactionId, String payload) {
    }

    private static final String INSERT_PENDING = """
            INSERT INTO event_outbox (transaction_id, status, payload)
            VALUES (?, 'PENDING', ?)
            """;

    private static final String MARK_SENT = """
            UPDATE event_outbox
               SET status = 'SENT', sent_at = SYSTIMESTAMP
             WHERE outbox_id = ?
            """;

    private static final String SELECT_PENDING = """
            SELECT outbox_id, transaction_id, payload
              FROM event_outbox
             WHERE status = 'PENDING'
             ORDER BY outbox_id
             FETCH FIRST ? ROWS ONLY
            """;

    private final JdbcTemplate jdbc;

    public EventOutboxDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserta PENDING y devuelve el outbox_id generado (KeyHolder). */
    public long insertPending(String transactionId, String payload) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            var ps = connection.prepareStatement(INSERT_PENDING, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, transactionId);
            ps.setString(2, payload);
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void markSent(long outboxId) {
        jdbc.update(MARK_SENT, outboxId);
    }

    public List<PendingEvent> listPending(int limit) {
        return jdbc.query(SELECT_PENDING,
                (rs, rowNum) -> new PendingEvent(
                        rs.getLong("outbox_id"),
                        rs.getString("transaction_id"),
                        rs.getString("payload")
                ),
                limit);
    }
}
