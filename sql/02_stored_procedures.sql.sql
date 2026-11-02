-- ============================================================
-- FinCard Tech - Procedimiento almacenado de autorizacion
-- gvenzl corre los init-scripts como SYS en el CDB (FREE):
-- nos conectamos AL schema del app para que el dueno sea FICARD
connect ficard/ficard@localhost/FREEPDB1
-- (PL/SQL, compatible Oracle 19c / 23ai)
--
-- RESPONSABILIDAD: la decision FINAL (fondos con lock pesimista,
-- aplicacion del cargo y registro de bitacora/movimiento) es
-- atomica dentro de este procedure. El core Java hace checks
-- "optimistas" previos (vigencia/estado cacheados en Redis) para
-- responder rapido, pero check definitivo pasa por aca:
-- SELECT ... FOR UPDATE bloquea la fila de la tarjeta hasta el
-- COMMIT/ROLLBACK, serializando cobros concurrentes => imposible
-- el doble gasto por carrera.
--
-- Llamado desde Java via CallableStatement (JDBC).
-- ============================================================

CREATE OR REPLACE PROCEDURE fic_process_authorization (
    p_transaction_id     IN  VARCHAR2,                -- UUID generado por el core
    p_card_number        IN  VARCHAR2,                -- PAN
    p_amount             IN  NUMBER,
    p_currency           IN  VARCHAR2,
    p_merchant_id        IN  VARCHAR2,
    p_merchant_name      IN  VARCHAR2,
    p_card_masked        IN  VARCHAR2,                 -- PAN enmascarado para bitacora
    o_decision           OUT VARCHAR2,                -- APPROVED | DECLINED
    o_response_code      OUT VARCHAR2,                -- estilo ISO 8583: 00/51/54/07/14
    o_decline_code       OUT VARCHAR2,                -- razon de negocio (o null)
    o_remaining_balance  OUT NUMBER,                  -- saldo resultante o disponible
    o_authorization_code OUT VARCHAR2                 -- 6 caracteres, solo si APPROVED
)
IS
    v_card_id        cards.card_id%TYPE;
    v_status         cards.status%TYPE;
    v_card_expiry    cards.card_expiry%TYPE;
    v_balance        cards.balance%TYPE;
    v_credit_limit   cards.credit_limit%TYPE;
    v_card_type      cards.card_type%TYPE;

    -- Excepciones de negocio (rango -20000..-20999)
    c_e_decline          CONSTANT PLS_INTEGER := -20051;
    c_e_card_not_found   CONSTANT PLS_INTEGER := -20014;

    e_decline        EXCEPTION;
    e_card_not_found EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_decline, -20051);
    PRAGMA EXCEPTION_INIT(e_card_not_found, -20014);

    --------------------------------------------------------------
    -- Declina: setea codigo de decision y lanza la senal
    --------------------------------------------------------------
    PROCEDURE decline (p_code VARCHAR2, p_rsn VARCHAR2) IS
    BEGIN
        o_decision      := 'DECLINED';
        o_response_code := p_code;
        o_decline_code  := p_rsn;
        RAISE_APPLICATION_ERROR(c_e_decline, 'CARD DECLINED');
    END decline;
BEGIN
    --------------------------------------------------------------
    -- 1) Bloqueo pesimista de la fila de la tarjeta.
    --
    --    Si otro procedure esta cobrando la misma tarjeta en este
    --    instante, espera aca hasta que ese commit termine. Sin el
    --    FOR UPDATE, SELECT devuelve saldo viejo y dos autorizaciones
    --    simultaneas podrian aprobarse con el mismo fondo.
    --    En prod real se anadiria WAIT/LOCK TIMEOUT.
    --------------------------------------------------------------
    BEGIN
        SELECT card_id, status, card_expiry, balance, credit_limit, card_type
          INTO v_card_id, v_status, v_card_expiry, v_balance, v_credit_limit, v_card_type
          FROM cards
         WHERE card_number = p_card_number
           FOR UPDATE;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(c_e_card_not_found, 'CARD NOT FOUND');
    END;

    --------------------------------------------------------------
    -- 2) Vigencia: la tarjeta expira al final del mes indicado.
    --    (Los checks de vigencia/estado se repiten aca por porque
    --    el core los hace optimo por cache de Redis; el DB es
    --    "la muerte final" de la fuente de verdad.)
    --------------------------------------------------------------
    IF TO_DATE('01/' || v_card_expiry, 'DD/MM/YY') +
       NUMTOYMINTERVAL(1, 'MONTH') - NUMTODSINTERVAL(1, 'DAY') < TRUNC(SYSDATE)
    THEN
        decline('54', 'CARD_EXPIRED');
    END IF;

    --------------------------------------------------------------
    -- 3) Estado de la tarjeta
    --------------------------------------------------------------
    IF v_status <> 'ACTIVE' THEN
        decline('07', 'CARD_BLOCKED');
    END IF;

    --------------------------------------------------------------
    -- 4) Fondos/cupo (check definitivo, ya transado con bloqueo)
    --
    --    DEBIT:  disponible = balance
    --    CREDIT: disponible = credit_limit - balance (balance=deuda)
    --------------------------------------------------------------
    IF v_card_type = 'DEBIT' THEN
        IF v_balance < p_amount THEN
            decline('51', 'INSUFFICIENT_FUNDS');
        END IF;
    ELSE
        IF (v_credit_limit - v_balance) < p_amount THEN
            decline('51', 'INSUFFICIENT_FUNDS');
        END IF;
    END IF;

    --------------------------------------------------------------
    -- 5) APROBADA: aplicar cargo, registrar bitacora y movimiento
    --------------------------------------------------------------
    IF v_card_type = 'DEBIT' THEN
        UPDATE cards
           SET balance = balance - p_amount,
               updated_at = SYSTIMESTAMP
         WHERE card_id = v_card_id;
    ELSE
        UPDATE cards
           SET balance = balance + p_amount,
               updated_at = SYSTIMESTAMP
         WHERE card_id = v_card_id;
    END IF;

    o_authorization_code := UPPER(DBMS_RANDOM.STRING('A', 6));

    INSERT INTO transactions (
        transaction_id, card_id, card_number_masked, decision, response_code,
        decline_code, amount, currency, merchant_id, merchant_name, authorization_code
    ) VALUES (
        p_transaction_id, v_card_id, p_card_masked, 'APPROVED', '00',
        NULL, p_amount, p_currency, p_merchant_id, p_merchant_name,
        o_authorization_code
    );

    INSERT INTO movements (
        transaction_id, card_id, movement_type, amount, balance_after
    ) VALUES (
        p_transaction_id, v_card_id, 'CHARGE', p_amount,
        CASE WHEN v_card_type = 'DEBIT'
             THEN v_balance - p_amount
             ELSE v_credit_limit - (v_balance + p_amount)
        END
    );

    o_decision      := 'APPROVED';
    o_response_code := '00';
    o_decline_code  := NULL;
    o_remaining_balance := CASE
                               WHEN v_card_type = 'DEBIT' THEN v_balance - p_amount
                               ELSE v_credit_limit - (v_balance + p_amount)
                           END;

    COMMIT;

EXCEPTION
    WHEN e_decline THEN
        -- Declinacion de negocio: grabamos la bitacora,
        -- no se toca saldo ni movements, y el COMMIT suelta el lock.
        IF v_card_id IS NOT NULL THEN
            INSERT INTO transactions (
                transaction_id, card_id, card_number_masked, decision, response_code,
                decline_code, amount, currency, merchant_id, merchant_name
            ) VALUES (
                p_transaction_id, v_card_id, p_card_masked, 'DECLINED',
                o_response_code, o_decline_code, p_amount, p_currency,
                p_merchant_id, p_merchant_name
            );
        END IF;

        o_remaining_balance := NVL(v_balance, 0);
        o_authorization_code := NULL;
        COMMIT;

    WHEN e_card_not_found THEN
        o_decision      := 'DECLINED';
        o_response_code := '14';
        o_decline_code  := 'CARD_NOT_FOUND';
        o_remaining_balance := NULL;
        o_authorization_code := NULL;
        COMMIT;

    WHEN OTHERS THEN
        -- Error inesperado: dejar todo como estaba y notificar al caller.
        ROLLBACK;
        o_decision      := NULL;
        o_response_code := '96';           -- "system malfunction" en ISO 8583
        o_decline_code  := 'INTERNAL_ERROR';
        o_remaining_balance := NULL;
        o_authorization_code := NULL;
        COMMIT;                            -- soltar locks: la decision es DECLINED
END;
/