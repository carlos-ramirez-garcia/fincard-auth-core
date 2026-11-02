package com.fincard.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincard.core.dto.CardAuthorizationResponse;
import com.fincard.core.service.AuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthorizationService authorizationService;
    @MockitoBean private com.fincard.core.cache.RedisCardCacheService redisCardCacheService;
    @MockitoBean private com.fincard.core.repository.CardRepository cardRepository;
    @MockitoBean private com.fincard.core.repository.AuthorizationDao authorizationDao;
    @MockitoBean private com.fincard.core.repository.EventOutboxDao eventOutboxDao;
    @MockitoBean private com.fincard.core.kafka.TransactionEventService transactionEventService;

    @Test
    @DisplayName("401: la API exige JWT en todos los paths no publicos")
    void postAuthorization_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/cards/authorizations")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("flujo completo: token REAL del DevTokenController y el body valido llega al service")
    void postAuthorization_withRealToken_flowsToService() throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/api/v1/dev/tokens"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertFalse(token.isBlank());

        var mockedResponse = new CardAuthorizationResponse(
                UUID.randomUUID(), "APPROVED", "00", null,
                "QPKXTE", new BigDecimal("1775.00"), Instant.now());
        when(authorizationService.authorize(any())).thenReturn(mockedResponse);

        String body = """
                {"cardNumber":"4539148803436467",
                 "cardExpiry":"10/29",
                 "amount":75.00,
                 "currency":"USD",
                 "merchantId":"MERCH-TEST",
                 "merchantName":"Novabase Merchant"}
                """;

        mockMvc.perform(post("/api/v1/cards/authorizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.authorizationCode").value("QPKXTE"));
    }

    @Test
    @DisplayName("400: body invalido se detiene en la validacion antes del service")
    void postAuthorization_invalidBody_returns400() throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/api/v1/dev/tokens"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        String invalidBody = """
                {"cardNumber":"123",
                 "cardExpiry":"not-a-date",
                 "amount":-5,
                 "currency":"XXXX",
                 "merchantId":"x"}
                """;

        mockMvc.perform(post("/api/v1/cards/authorizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());

        verify(authorizationService, never()).authorize(any());
    }
}
