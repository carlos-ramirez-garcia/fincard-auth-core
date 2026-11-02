package com.fincard.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincard.audit.repository.AuditDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditConsumerTest {

    private static final String EVENT_JSON = """
            {"eventId":"e-1",
             "transactionId":"txn-1",
             "maskedCardNumber":"4539****6467",
             "decision":"APPROVED",
             "responseCode":"00",
             "authorizationCode":"QPKXTE",
             "amount":75.00,
             "currency":"USD",
             "merchantId":"MERCH-TEST",
             "merchantName":"Novabase Merchant",
             "timestamp":"2026-11-02T14:49:59Z"}
            """;

    @Mock
    private AuditDao dao;

    private AuditConsumer consumer;
    private final ObjectMapper realMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        consumer = new AuditConsumer(dao, realMapper);
    }

    @Test
    @DisplayName("evento nuevo: exists y luego insert, en ese orden")
    void consume_newEvent_inserts() {
        when(dao.exists("txn-1")).thenReturn(false);

        consumer.onEvent(EVENT_JSON);

        InOrder inOrder = inOrder(dao);
        inOrder.verify(dao).exists("txn-1");

        ArgumentCaptor<AuditEventMessage> captor = ArgumentCaptor.forClass(AuditEventMessage.class);
        inOrder.verify(dao).insert(captor.capture());

        assertEquals("txn-1", captor.getValue().transactionId());
        assertEquals("APPROVED", captor.getValue().decision());
        assertEquals("QPKXTE", captor.getValue().authorizationCode());
    }

    @Test
    @DisplayName("duplicado: exists=true el insert nunca se llama")
    void consume_duplicate_skipsInsert() {
        when(dao.exists("txn-1")).thenReturn(true);

        consumer.onEvent(EVENT_JSON);

        verify(dao, never()).insert(any());
    }

    @Test
    @DisplayName("fallo del insert: relanza IllegalStateException")
    void consume_daoFailure_rethrows() {
        when(dao.exists("txn-1")).thenReturn(false);
        doThrow(new RuntimeException("ORA-00001 unique violated"))
                .when(dao).insert(any());

        assertThrows(IllegalStateException.class,
                () -> consumer.onEvent(EVENT_JSON));
    }

    @Test
    @DisplayName("payload malformado: relanza IllegalStateException")
    void consume_malformedPayload_rethrows() {
        assertThrows(IllegalStateException.class,
                () -> consumer.onEvent("{not-valid-json"));
    }
}
