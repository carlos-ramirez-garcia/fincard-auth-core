package com.fincard.core.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincard.core.kafka.TransactionEventService;
import com.fincard.core.repository.EventOutboxDao;
import com.fincard.core.repository.EventOutboxDao.PendingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventServiceTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private EventOutboxDao outboxDao;

    private TransactionEventService service;
    private final ObjectMapper realMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new TransactionEventService(kafkaTemplate, outboxDao, realMapper);
    }



    @Test
    @DisplayName("APPROVED: outbox recibe el payload y ACK promueve a SENT")
    void publish_ok_marksSent() {
        when(outboxDao.insertPending(eq("txn-1"), anyString())).thenReturn(1L);
        when(kafkaTemplate.send(eq("transaction-events"), eq("txn-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.recordAndPublishApproved("txn-1", "4539****6467",
                new java.math.BigDecimal("75.00"), "USD",
                "MERCH-TEST", "Novabase Merchant", "QPKXTE");

        verify(outboxDao).insertPending(eq("txn-1"), anyString());
        verify(outboxDao).markSent(1L);
    }


    @Test
    @DisplayName("el JSON que viaja a Kafka lleva decisión, PAN mock y auth-code")
    void publish_payloadContract() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        when(outboxDao.insertPending(eq("txn-1"), anyString())).thenReturn(1L);
        when(kafkaTemplate.send(eq("transaction-events"), eq("txn-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.recordAndPublishApproved("txn-1", "4539****6467",
                new java.math.BigDecimal("75.00"), "USD",
                "MERCH-TEST", "Novabase Merchant", "QPKXTE");

        verify(kafkaTemplate).send(eq("transaction-events"), eq("txn-1"), payload.capture());
        String json = payload.getValue();
        assertTrue(json.contains("\"APPROVED\""));
        assertTrue(json.contains("4539****6467"));
        assertTrue(json.contains("\"QPKXTE\""));
    }


    @Test
    @DisplayName("Kafka caído: NO explota, el outbox queda PENDING (retryPending lo hará)")
    void publish_kafkaDown_keepsPending() {
        when(outboxDao.insertPending(eq("txn-1"), anyString())).thenReturn(1L);
        when(kafkaTemplate.send(eq("transaction-events"), eq("txn-1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("kafka connection failed")));


        assertDoesNotThrow(() -> service.recordAndPublishApproved("txn-1", "4539****6467",
                new java.math.BigDecimal("75.00"), "USD",
                "MERCH-TEST", "Novabase Merchant", "QPKXTE"));

        verify(outboxDao, never()).markSent(anyLong());
    }


    @Test
    @DisplayName("retryPending: lista pendientes, RE-publica y marca SENT al ack")
    void retry_rescuesPendingEvents() {
        var pending = new PendingEvent(7L, "txn-1", "{\"hold\":\"json\"}");
        when(outboxDao.listPending(20))
                .thenReturn(List.of(pending));
        when(kafkaTemplate.send(eq("transaction-events"), eq("txn-1"),
                eq("{\"hold\":\"json\"}")))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.retryPending();

        verify(outboxDao).markSent(7L);
    }


    @Test
    @DisplayName("retryPending con outbox vacio: 0 llamadas a kafka")
    void retry_emptyOutbox_isSilent() {
        when(outboxDao.listPending(20)).thenReturn(List.of());

        service.retryPending();

        verifyNoInteractions(kafkaTemplate);
    }
}
