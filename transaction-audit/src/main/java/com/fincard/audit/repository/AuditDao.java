package com.fincard.audit.repository;
import com.fincard.audit.api.AuditEntry;
import com.fincard.audit.kafka.AuditEventMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AuditDao {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE = """
            SELECT transaction_id, masked_card_number, decision, response_code,
                   authorization_code, amount, currency,
                   merchant_id, merchant_name, transaction_ts, received_at
              FROM audit_transactions
            """;

    public AuditDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(String transactionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_transactions WHERE transaction_id = ?",
                Integer.class, transactionId);
        return count != null && count > 0;
    }

    public void insert(AuditEventMessage evt) {
        jdbc.update("""
                INSERT INTO audit_transactions
                    (transaction_id, masked_card_number, decision, response_code,
                     decline_code, authorization_code, amount, currency,
                     merchant_id, merchant_name, transaction_ts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                evt.transactionId(),
                evt.maskedCardNumber(),
                evt.decision(),
                evt.responseCode(),
                evt.declineCode(),          // null hoy (solo llegan APPROVED) — escritura es record de eso
                evt.authorizationCode(),    // null si fututo DECLINED
                evt.amount(),
                evt.currency(),
                evt.merchantId(),
                evt.merchantName(),
                Timestamp.from(evt.timestamp())
        );
    }

    public List<AuditEntry> list(String maskedCardNumber, int limit) {
        String sql = SELECT_BASE
                + (maskedCardNumber == null ? "" : " WHERE masked_card_number = ?")
                + " ORDER BY received_at DESC FETCH FIRST ? ROWS ONLY";

        Object[] args = (maskedCardNumber == null)
                ? new Object[] { limit }
                : new Object[] { maskedCardNumber, limit };

        return jdbc.query(sql, (rs, i) -> new AuditEntry(
                rs.getString("transaction_id"),
                rs.getString("masked_card_number"),
                rs.getString("decision"),
                //rs.getString("reviewCode"),
                rs.getString("response_code"),
                rs.getString("authorization_code"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("merchant_id"),
                rs.getString("merchant_name"),
                rs.getTimestamp("transaction_ts").toInstant(),
                rs.getTimestamp("received_at").toInstant()
        ), args);
    }
}
