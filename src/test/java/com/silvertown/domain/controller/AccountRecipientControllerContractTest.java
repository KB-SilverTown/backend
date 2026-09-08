package com.silvertown.domain.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.controller.AccountController;
import com.silvertown.domain.account.dto.AccountResponse;
import com.silvertown.domain.account.service.AccountService;
import com.silvertown.domain.recipient.controller.RecipientController;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.global.security.AuthenticatedUserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountRecipientControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AccountService accountService = userId -> List.of(new AccountResponse(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "004", "***-***-6789", "KB 주거래통장", 1_250_000L,
                "DEPOSIT", true, null));
        RecipientService recipientService = (userId, request) -> List.of(
                new RecipientCandidateResponse(
                        UUID.fromString("20000000-0000-0000-0000-000000000001"),
                        "홍길순", "자녀", "004", "***-***-0789", "HISTORY", null));
        AuthenticatedUserId authenticatedUserId = new AuthenticatedUserId();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AccountController(accountService, authenticatedUserId),
                        new RecipientController(recipientService, authenticatedUserId))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void accountResponseMatchesApiContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/accounts").principal(authentication()))
                .andExpect(status().isOk()).andReturn();
        JsonNode account = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get(0);
        assertEquals("10000000-0000-0000-0000-000000000001", account.get("accountId").asText());
        assertEquals("***-***-6789", account.get("accountNumberMasked").asText());
        assertEquals(1_250_000, account.get("balance").asLong());
        assertFalse(account.has("accountNumberEncrypted"));
    }

    @Test
    void recipientCandidateResponseMatchesApiContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recipients/candidates")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"홍길순\",\"contacts\":[{\"displayName\":\"홍길순\",\"phoneNumber\":\"01012345678\"}]}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode recipient = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get(0);
        assertEquals("홍길순", recipient.get("displayName").asText());
        assertEquals("HISTORY", recipient.get("source").asText());
        assertEquals("***-***-0789", recipient.get("accountNumberMasked").asText());
        assertFalse(recipient.has("accountNumberEncrypted"));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(USER_ID.toString(), "", List.of());
    }
}
