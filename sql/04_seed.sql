------------------------------------------------------------
-- SEED: tarjetas de prueba (los 6 escenarios)
-- gvenzl corre los init-scripts como SYS en el CDB (FREE):
-- nos conectamos AL schema del app para que el dueno sea FICARD
connect ficard/ficard@localhost/FREEPDB1
-- Corre automaticamente en el primer arranque del compose:
-- el volumen mapea ./sql -> /opt/oracle/scripts/startup
-- y la imagen gvenzl ejecuta los .sql en orden alfabetico
-- (01 schema -> 02 procedure -> 03 audit -> 04 seed)
--
-- NOTA 23ai: los comentarios van SIEMPRE en linea propia;
-- un "--" despues del ';' en la misma linea dispara
-- ORA-03048 (quirk del sqlplus 23.7+)
--
-- IDEMPOTENCIA: elimina antes de insertar para poder
-- re-ejecutar sin chocar la UNIQUE de card_number
--
-- ESCENARIOS:
--   4539148803436467 -> APPROVED (00)
--   5211000000001234 -> 51 INSUFFICIENT_FUNDS (monto > saldo)
--   4111111111111111 -> 07 CARD_BLOCKED (early-decline Java)
--   4024007196559937 -> 54 CARD_EXPIRED (derivado, exp 01/25)
--   3782822463100051 -> credito (saldo = deuda, limite 5000:
--   disponible = 1527.5; aprobar monto <= ese, declinar 51 el resto)
------------------------------------------------------------
DELETE FROM cards;

INSERT INTO cards (card_number, card_expiry, status, card_type, balance) VALUES ('4539148803436467', '10/29', 'ACTIVE', 'DEBIT', 1850);

INSERT INTO cards (card_number, card_expiry, status, card_type, balance) VALUES ('5211000000001234', '10/29', 'ACTIVE', 'DEBIT', 42.5);

INSERT INTO cards (card_number, card_expiry, status, card_type, balance) VALUES ('4111111111111111', '10/30', 'BLOCKED', 'DEBIT', 500);

INSERT INTO cards (card_number, card_expiry, status, card_type, balance) VALUES ('4024007196559937', '01/25', 'ACTIVE', 'DEBIT', 1000);

INSERT INTO cards (card_number, card_expiry, status, card_type, balance, credit_limit) VALUES ('3782822463100051', '10/27', 'ACTIVE', 'CREDIT', 3472.5, 5000);

COMMIT;
