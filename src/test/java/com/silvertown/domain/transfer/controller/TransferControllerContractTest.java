package com.silvertown.domain.transfer.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferPrepareRequest;
import com.silvertown.domain.transfer.dto.TransferPrepareResponse;
import com.silvertown.domain.transfer.dto.TransferRecipientResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.transfer.dto.TransferConfirmRequest;
import com.silvertown.domain.transfer.dto.TransferConfirmResponse;
import com.silvertown.domain.transfer.dto.TransferPinRequest;
import com.silvertown.domain.transfer.dto.TransferAuthenticationResponse;
import com.silvertown.domain.transfer.dto.TransferResultResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationStartResponse;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmRequest;
import com.silvertown.domain.transfer.dto.GuardianVerificationConfirmResponse;
import com.silvertown.domain.transfer.service.TransferService;
import com.silvertown.domain.transfer.service.impl.TransferServiceImpl;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.common.exception.GlobalExceptionHandler;
import com.silvertown.global.common.filter.RequestIdFilter;
import com.silvertown.global.security.AuthenticatedUserId;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransferControllerContractTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TRANSFER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        TransferRecipientResponse recipient = new TransferRecipientResponse(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "홍길순", "004", "***-***-0789");
        TransferService service = new TransferService() {
            public AmountValidationResponse validateAmount(AmountValidationRequest request) {
                return new AmountValidationResponse(50000L, List.of(50000L), 50000L, false);
            }
            public TransferPrepareResponse prepare(UUID userId, TransferPrepareRequest request) {
                return new TransferPrepareResponse(
                        TRANSFER_ID, "DRAFT", "RISK_CHECK", recipient, 50000L, false,
                        "홍길순 님에게 50,000원을 보내시겠어요?", OffsetDateTime.now());
            }
            public TransferResponse get(UUID userId, UUID transferId) {
                return null;
            }
            public TransferConfirmResponse confirm(UUID userId, UUID transferId, TransferConfirmRequest request) {
                return null;
            }
            public void registerOrChangePin(UUID userId, TransferPinRequest request) { }
            public TransferAuthenticationResponse authenticate(
                    UUID userId, UUID transferId, String confirmationToken, TransferPinRequest request) {
                return null;
            }
            public TransferResultResponse execute(
                    UUID userId, UUID transferId, String confirmationToken, String idempotencyKey) {
                return null;
            }
            public GuardianVerificationStartResponse startGuardianVerification(UUID userId, UUID transferId,
                    GuardianVerificationStartRequest request) { return null; }
            public GuardianVerificationConfirmResponse verifyGuardianVerification(UUID userId, UUID transferId,
                    UUID verificationId, GuardianVerificationConfirmRequest request) { return null; }
            public String getDemoGuardianVerificationCode(UUID userId, UUID transferId, UUID verificationId) {
                return "123456";
            }
            public TransferResponse cancel(UUID userId, UUID transferId) {
                return null;
            }
            public void cancelUnexecuted(UUID userId, UUID transferId) { }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TransferController(service, new AuthenticatedUserId()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void amountValidationResponseMatchesContract() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/transfers/validate-amount")
                        .principal(authentication()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":\"5만원\",\"recognizedAmount\":50000}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(response.has("amountReconfirmRequired"));
        assertTrue(response.has("confirmedAmount"));
    }

    @Test
    void amountValidationReturnsStandardBadRequestForInvalidCandidates() throws Exception {
        JsonNode response = objectMapper.readTree(amountValidationMockMvc().perform(post("/api/transfers/validate-amount")
                        .principal(authentication()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recognizedAmount\":50000,\"amountCandidates\":[50000,500000]}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsByteArray());

        assertTrue(response.has("requestId"));
        assertFalse(response.get("requestId").isNull());
        assertTrue(response.has("fieldErrors"));
        assertTrue(response.get("fieldErrors").isArray());
        assertTrue(response.get("fieldErrors").isEmpty());
        assertEquals(ErrorCode.INVALID_TRANSFER_AMOUNT.getCode(), response.get("code").asText());
        assertEquals(ErrorCode.INVALID_TRANSFER_AMOUNT.getMessage(), response.get("message").asText());
    }

    @Test
    void amountValidationRequiresTranscript() throws Exception {
        JsonNode response = objectMapper.readTree(amountValidationMockMvc().perform(post("/api/transfers/validate-amount")
                        .principal(authentication()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recognizedAmount\":50000}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsByteArray());

        assertEquals(ErrorCode.INVALID_TRANSFER_AMOUNT.getCode(), response.get("code").asText());
    }

    private MockMvc amountValidationMockMvc() {
        TransferService service = new TransferServiceImpl(
                null, null, null, null, null, objectMapper, Clock.systemUTC(), null, null);
        return MockMvcBuilders.standaloneSetup(
                        new TransferController(service, new AuthenticatedUserId()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void prepareResponseNeverContainsConfirmationTokenOrSensitiveAccountData() throws Exception {
        JsonNode response = objectMapper.readTree(mockMvc.perform(post("/api/transfers/prepare")
                        .principal(authentication()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"10000000-0000-0000-0000-000000000001\","
                                + "\"recipientId\":\"20000000-0000-0000-0000-000000000001\","
                                + "\"amount\":50000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        assertFalse(response.has("confirmationToken"));
        assertFalse(response.has("confirmationTokenExpiresAt"));
        assertFalse(response.toString().contains("accountNumberEncrypted"));
        assertTrue(response.toString().contains("accountNumberMasked"));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(USER_ID.toString(), "", List.of());
    }
}
