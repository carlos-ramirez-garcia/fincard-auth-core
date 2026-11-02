package com.fincard.core.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;

@Repository
public class AuthorizationDao {

    /** Resultado de la decisión FINAL (sale del procedure en Oracle). */
    public record OracleDecision(
            String decision,
            String responseCode,
            String declineCode,
            BigDecimal remainingBalance,
            String authorizationCode
    ) {
    }

    private static final String CALL = "{call fic_process_authorization(?,?,?,?,?,?,?,?,?,?,?,?)}";

    private final JdbcTemplate jdbc;

    public AuthorizationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OracleDecision callAuthorizationProcedure(String transactionId,
                                                     String cardNumber,
                                                     BigDecimal amount,
                                                     String currency,
                                                     String merchantId,
                                                     String merchantName,
                                                     String cardMasked) {
        return jdbc.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(CALL)) {

                int i = 1;
                // ——— los 7 IN (mismo orden que la firma del procedure) ———
                cs.setString(i++, transactionId);
                cs.setString(i++, cardNumber);
                cs.setBigDecimal(i++, amount);
                cs.setString(i++, currency);
                cs.setString(i++, merchantId);
                cs.setString(i++, merchantName);
                cs.setString(i++, cardMasked);

                // ——— los 5 OUT ((posición 8 a 12) ———
                cs.registerOutParameter(i++, Types.VARCHAR);  // o_decision
                cs.registerOutParameter(i++, Types.VARCHAR);  // o_response_code
                cs.registerOutParameter(i++, Types.VARCHAR);  // o_decline_code
                cs.registerOutParameter(i++, Types.NUMERIC);  // o_remaining_balance
                cs.registerOutParameter(i++, Types.VARCHAR);  // o_authorization_code

                cs.execute();                                  // ← el procedure corre EN Oracle

                return new OracleDecision(
                        cs.getString(8),
                        cs.getString(9),
                        cs.getString(10),
                        cs.getBigDecimal(11),
                        cs.getString(12)
                );
            }
        });
    }
}
