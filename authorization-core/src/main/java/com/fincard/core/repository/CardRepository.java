package com.fincard.core.repository;

import com.fincard.core.domain.CardStatus;
import com.fincard.core.domain.CardType;
import com.fincard.core.domain.exception.CardNotFoundException;
import com.fincard.core.domain.model.CardSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CardRepository {

    private static final String SELECT_BY_PAN = """
            SELECT card_number, card_expiry, status, card_type,
                   balance, credit_limit, currency
              FROM cards
             WHERE card_number = ?
            """;

    private final JdbcTemplate jdbc;

    public CardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CardSnapshot findByPan(String pan) {
        var rows = jdbc.query(
                SELECT_BY_PAN,
                (rs, rowNum) -> new CardSnapshot(
                        rs.getString("card_number"),
                        rs.getString("card_expiry"),
                        CardStatus.valueOf(rs.getString("status")),
                        CardType.valueOf(rs.getString("card_type")),
                        rs.getBigDecimal("balance"),
                        rs.getBigDecimal("credit_limit"),
                        rs.getString("currency")
                ),
                pan
        );

        if (rows.isEmpty()) {
            throw new CardNotFoundException("No card is registered with this number");
        }
        return rows.getFirst();
    }
}
