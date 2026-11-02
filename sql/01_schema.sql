------------------------------------------------------------
-- TARJETAS: estado, vigencia, saldo y limite de credito
-- ------------------------------------------------------------
-- gvenzl corre los init-scripts como SYS en el CDB (FREE):
-- nos conectamos AL schema del app para que el dueno sea FICARD
-- (instruccion oficial del maintainer)
connect ficard/ficard@localhost/FREEPDB1

CREATE TABLE cards (
    card_id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    card_number     VARCHAR2(19)  NOT NULL,          -- PAN; en prod va ofuscado/tokenizado
    card_expiry     VARCHAR2(5)   NOT NULL,          -- MM/YY
    status          VARCHAR2(10)  NOT NULL,          -- ACTIVE | BLOCKED
    card_type       VARCHAR2(8)   NOT NULL,          -- DEBIT  | CREDIT
    balance         NUMBER(14, 2) NOT NULL,          -- saldo (debito) o deuda (credito)
    credit_limit    NUMBER(14, 2)          DEFAULT 0 NOT NULL,
    currency        CHAR(3)       DEFAULT 'USD' NOT NULL,
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_cards_number UNIQUE (card_number),
    CONSTRAINT ck_cards_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT ck_cards_type   CHECK (card_type IN ('DEBIT', 'CREDIT'))
);

--CREATE INDEX ix_cards_expiry ON cards (card_expiry);

-- ------------------------------------------------------------
-- TRANSACCIONES: bitacora de toda decision (aprobada o declinada)
-- ------------------------------------------------------------
CREATE TABLE transactions (
    transaction_id      VARCHAR2(36)  PRIMARY KEY,      -- UUID generado en el core
    card_id             NUMBER        NOT NULL REFERENCES cards (card_id),
    card_number_masked  VARCHAR2(19)  NOT NULL,         -- nunca el PAN completo
    decision            VARCHAR2(10)  NOT NULL,         -- APPROVED | DECLINED
    response_code       VARCHAR2(2)   NOT NULL,         -- estilo ISO 8583: 00, 51, 07...
    decline_code        VARCHAR2(30),                   -- INSUFFICIENT_FUNDS, CARD_EXPIRED...
    amount              NUMBER(14, 2) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    merchant_id         VARCHAR2(40)  NOT NULL,
    merchant_name       VARCHAR2(120),
    authorization_code  VARCHAR2(6),
    created_at          TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_tx_decision CHECK (decision IN ('APPROVED', 'DECLINED'))
);

CREATE INDEX ix_transactions_card_id ON transactions (card_id);
CREATE INDEX ix_transactions_created ON transactions (created_at);

-- ------------------------------------------------------------
-- MOVIMIENTOS: aplicacion de cargos al saldo (uno por transaccion
-- aprobada). Necesario para conciliar el saldo con la historica.
-- ------------------------------------------------------------
CREATE TABLE movements (
    movement_id     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id  VARCHAR2(36)  NOT NULL REFERENCES transactions (transaction_id),
    card_id         NUMBER        NOT NULL REFERENCES cards (card_id),
    movement_type   VARCHAR2(8)   DEFAULT 'CHARGE' NOT NULL,   -- CHARGE | REFUND | REVERSAL
    amount          NUMBER(14, 2) NOT NULL,
    balance_after   NUMBER(14, 2) NOT NULL,          -- saldo snapshot post-cargo (auditoria)
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_movements_txn UNIQUE (transaction_id)   -- idempotencia: no doble cargo
);

-- ------------------------------------------------------------
-- Trazabilidad de eventos enviados a Kafka (para reproceso)
-- ------------------------------------------------------------
CREATE TABLE event_outbox (
    outbox_id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id  VARCHAR2(36) NOT NULL,
    status          VARCHAR2(12) DEFAULT 'PENDING' NOT NULL, -- PENDING | SENT | FAILED
    payload         CLOB         NOT NULL,                   -- JSON serializado del evento
    created_at      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    sent_at         TIMESTAMP,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
