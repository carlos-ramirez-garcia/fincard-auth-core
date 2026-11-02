------------------------------------------------------------
-- AUDIT_TRANSACTIONS: espejo async de las transacciones
-- gvenzl corre los init-scripts como SYS en el CDB (FREE):
-- nos conectamos AL schema del app para que el dueno sea FICARD
connect ficard/ficard@localhost/FREEPDB1

-- APROBADAS, llenado por el microservicio transaction-audit
-- a partir del topic Kafka `transaction-events`.
--
-- Diseño clave: TRANSACTION_ID UNIQUE → el constraint
-- ES la garantía de idempotencia de nivel BD: aunque el
-- evento llegue dos veces (at-least-once), la segunda
-- escritura VIOLA el constraint y el consumer la ignora.
------------------------------------------------------------
CREATE TABLE audit_transactions (
    audit_id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id      VARCHAR2(36)  NOT NULL,          -- garantía del productor: estable entre reintentos
    masked_card_number  VARCHAR2(19)  NOT NULL,          -- PAN SIEMPRE enmascarado (nunca completo)
    decision            VARCHAR2(10)  NOT NULL,          -- hoy: APPROVED (contrato garantiza solo APPROVED)
    response_code       VARCHAR2(2)   NOT NULL,          -- estilo ISO 8583: 00, etc.
    decline_code        VARCHAR2(30),                    -- presente solo si DECLINED (defensivo)
    authorization_code  VARCHAR2(20),                    -- presente solo si APPROVED
    amount              NUMBER(14, 2) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    merchant_id         VARCHAR2(40)  NOT NULL,
    merchant_name       VARCHAR2(120),
    transaction_ts      TIMESTAMP(9)  NOT NULL,          -- el timestamp del evento de origen (UTC)
    received_at         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,  -- cuando EL AUDIT lo grabó
    CONSTRAINT uq_audit_txn UNIQUE (transaction_id),     -- ← EL idempotencia de BD
    CONSTRAINT ck_audit_decision CHECK (decision IN ('APPROVED', 'DECLINED'))
);

CREATE INDEX ix_audit_card ON audit_transactions (masked_card_number);
--CREATE INDEX ix_audit_received ON audit_transactions (received_at);
