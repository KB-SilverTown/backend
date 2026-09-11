package com.silvertown.domain.voice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.enums.DialogueInputType;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisCommand;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisResult;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class OpenAiVoiceTurnAnalysisClientTest {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-5.6-terra";
    private static final String API_KEY = "test-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsSafeReaskWithoutCallingOpenAiWhenSttConfidenceIsLow() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        OpenAiVoiceTurnAnalysisClient client = client(restTemplate, API_KEY);

        VoiceTurnAnalysisResult result = client.analyze(command("0.69"));

        assertEquals(DialogueStep.AWAITING_INPUT, result.getNextStep());
        assertEquals(VoiceIntent.UNKNOWN, result.getIntent());
        assertEquals(VoiceNextAction.REASK_INPUT, result.getNextAction());
        assertEquals(VoiceRequestedFunction.NONE, result.getRequestedFunction());
        verify(restTemplate, never()).exchange(
                any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void parsesValidatedStructuredAnalysisAndBuildsResponsesApiRequest() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(validDraft()));
        OpenAiVoiceTurnAnalysisClient client = client(restTemplate, API_KEY);

        VoiceTurnAnalysisResult result = client.analyze(command("0.95"));

        assertEquals(DialogueStep.AWAITING_AMOUNT, result.getNextStep());
        assertEquals(VoiceIntent.TRANSFER, result.getIntent());
        assertEquals(new BigDecimal("0.96"), result.getConfidence());
        assertEquals(VoiceRequestedFunction.TRANSFER_RECIPIENT_CANDIDATES, result.getRequestedFunction());
        assertEquals("홍길동", result.getSlots().get("recipient"));
        assertEquals(300000, result.getDisplayCard().path("amount").asInt());

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(URI.create(ENDPOINT)), eq(HttpMethod.POST), entityCaptor.capture(), eq(JsonNode.class));
        HttpEntity<?> entity = entityCaptor.getValue();
        assertEquals("Bearer " + API_KEY, entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        JsonNode request = (JsonNode) entity.getBody();
        assertEquals(MODEL, request.path("model").asText());
        assertFalse(request.path("store").asBoolean(true));
        assertEquals("json_schema", request.path("text").path("format").path("type").asText());
        assertEquals("voice_turn_analysis", request.path("text").path("format").path("name").asText());
        assertFalse(request.path("text").path("format").path("schema").path("additionalProperties")
                .asBoolean(true));
        JsonNode properties = request.path("text").path("format").path("schema").path("properties");
        assertTrue(properties.path("intent").path("enum").toString().contains("TRANSFER"));
        assertFalse(properties.path("nextAction").path("enum").toString().contains("ASK_FINAL_APPROVAL"));
        assertFalse(properties.path("nextAction").path("enum").toString().contains("ASK_PIN"));
        assertTrue(properties.path("nextStep").path("enum").toString().contains("RISK_CHECK"));
        assertFalse(properties.path("nextStep").path("enum").toString().contains("WAITING_FINAL_APPROVAL"));
        assertFalse(properties.path("nextStep").path("enum").toString().contains("HELD"));
        assertFalse(properties.path("nextStep").path("enum").toString().contains("COMPLETED"));
        assertFalse(properties.path("nextStep").path("enum").toString().contains("CANCELLED"));
        assertFalse(properties.path("requestedFunction").path("enum").toString().contains("BILL_OCR_DRAFT"));
        assertFalse(properties.has("ttsText"));
        assertFalse(properties.has("ttsSsml"));
        assertFalse(properties.has("displayCardJson"));
    }

    @Test
    void blocksUnapprovedFunctionNamesFromTheModelResponse() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String draft = validDraft().replace("TRANSFER_RECIPIENT_CANDIDATES", "TRANSFER_EXECUTE");
        mockResponse(restTemplate, structuredResponse(draft));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(command("0.95")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void returnsSafeReconfirmWithoutCallingOpenAiForMediumConfidence() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(command("0.89"));

        assertEquals(DialogueStep.RECONFIRMING, result.getNextStep());
        assertEquals(VoiceIntent.UNKNOWN, result.getIntent());
        assertEquals(VoiceRequestedFunction.NONE, result.getRequestedFunction());
        assertTrue(result.getSlots().isEmpty());
        verify(restTemplate, never()).exchange(
                any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void rejectsCommandsMissingFieldsUsedToBuildTheAnalysisInput() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        OpenAiVoiceTurnAnalysisClient client = client(restTemplate, API_KEY);

        assertInvalidRequest(client, command(null, DialogueStep.AWAITING_INPUT, new BigDecimal("0.95"), DialogueInputType.VOICE));
        assertInvalidRequest(client, command(VoiceFlowType.TRANSFER, null, new BigDecimal("0.95"), DialogueInputType.VOICE));
        assertInvalidRequest(client, command(VoiceFlowType.TRANSFER, DialogueStep.AWAITING_INPUT, null, DialogueInputType.VOICE));
        assertInvalidRequest(client, command(VoiceFlowType.TRANSFER, DialogueStep.AWAITING_INPUT, new BigDecimal("0.95"), null));

        verify(restTemplate, never()).exchange(
                any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void rejectsMissingApiKeyBeforeMakingExternalCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client(restTemplate, " ").analyze(generalFinanceCommand("금융 일정 알려줘")));

        assertEquals(ErrorCode.LLM_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void usesConstrainedTransferFallbackWhenOpenAiConnectionFails() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.exchange(
                        any(URI.class),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(command("0.95"));

        assertEquals(DialogueStep.AWAITING_RECIPIENT, result.getNextStep());
        assertEquals(VoiceIntent.TRANSFER, result.getIntent());
        assertEquals(VoiceNextAction.ASK_RECIPIENT, result.getNextAction());
        assertEquals(VoiceRequestedFunction.NONE, result.getRequestedFunction());
        assertEquals("홍길동", result.getSlots().get("recipient"));
        assertNull(result.getSlots().get("amount"));
        assertEquals(List.of(300000L, 400000L), result.getSlots().get("amountCandidates"));
    }

    @Test
    void doesNotUseTransferFallbackForGeneralFinanceConnectionFailures() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.exchange(
                        any(URI.class),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client(restTemplate, API_KEY).analyze(generalFinanceCommand("금융 일정 알려줘")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void rejectsRequestedFunctionThatDoesNotMatchTheIntent() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String draft = validDraft().replace("\"TRANSFER\"", "\"ACCOUNT_INQUIRY\"");
        mockResponse(restTemplate, structuredResponse(draft));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(command("0.95")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void rejectsRequestedFunctionThatDoesNotMatchTheNextAction() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String draft = validDraft()
                .replace("\"AWAITING_AMOUNT\"", "\"AWAITING_RECIPIENT\"")
                .replace("\"ASK_AMOUNT\"", "\"ASK_RECIPIENT\"")
                .replace("TRANSFER_RECIPIENT_CANDIDATES", "TRANSFER_RISK_CHECK");
        mockResponse(restTemplate, structuredResponse(draft));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(command("0.95")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void downgradesBillFunctionWhenTheTranscriptDoesNotContainBillDomainTerms() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(billInquiryDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(generalFinanceCommand(
                "이번 달 금융 일정 알려줘"));

        assertEquals(VoiceRequestedFunction.FINANCIAL_TASK_CLASSIFY, result.getRequestedFunction());
    }

    @Test
    void keepsBillFunctionWhenTheTranscriptContainsBillDomainTerms() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(billInquiryDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(generalFinanceCommand(
                "이번 달 공과금이 얼마인지 알려줘"));

        assertEquals(VoiceRequestedFunction.BILL_INQUIRY, result.getRequestedFunction());
    }

    @Test
    void rejectsBillPaymentForABillInquiryTranscript() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(billPaymentDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(generalFinanceCommand(
                "이번 달 공과금이 얼마예요?"));

        assertEquals(VoiceRequestedFunction.FINANCIAL_TASK_CLASSIFY, result.getRequestedFunction());
    }

    @Test
    void rejectsBillPaymentForABillArrivalTranscript() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(billPaymentDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(generalFinanceCommand(
                "고지서 왔어?"));

        assertEquals(VoiceRequestedFunction.FINANCIAL_TASK_CLASSIFY, result.getRequestedFunction());
    }

    @Test
    void keepsBillPaymentOnlyForABillPaymentTranscript() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(billPaymentDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(generalFinanceCommand(
                "고지서를 촬영해서 납부할게요"));

        assertEquals(VoiceRequestedFunction.BILL_PAYMENT, result.getRequestedFunction());
    }

    @Test
    void acceptsNaturalBillAmountPhraseWithoutAnExplicitBillName() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(billInquiryDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(generalFinanceCommand(
                "납부할 금액 알려줘"));

        assertEquals(VoiceRequestedFunction.BILL_INQUIRY, result.getRequestedFunction());
    }

    @Test
    void acceptsTransferRiskCheckOnlyWithTheRiskCheckTransition() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String draft = validDraft()
                .replace("\"AWAITING_AMOUNT\"", "\"RISK_CHECK\"")
                .replace("\"ASK_AMOUNT\"", "\"NONE\"")
                .replace("TRANSFER_RECIPIENT_CANDIDATES", "TRANSFER_RISK_CHECK");
        mockResponse(restTemplate, structuredResponse(draft));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(command("0.95"));

        assertEquals(DialogueStep.RISK_CHECK, result.getNextStep());
        assertEquals(VoiceNextAction.NONE, result.getNextAction());
        assertEquals(VoiceRequestedFunction.TRANSFER_RISK_CHECK, result.getRequestedFunction());
    }

    @Test
    void rejectsPostRiskPinActionFromTheAnalysisPort() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(pinRequestDraft()));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(command("0.95")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void rejectsModelOutputContainingPresentationFields() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String draft = validDraft().replace(
                "\"requestedFunction\": \"TRANSFER_RECIPIENT_CANDIDATES\"",
                "\"ttsText\": \"임의 안내\",\n"
                        + "  \"requestedFunction\": \"TRANSFER_RECIPIENT_CANDIDATES\"");
        mockResponse(restTemplate, structuredResponse(draft));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(command("0.95")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void returnsReconfirmingForMultipleAmountCandidates() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(ambiguousAmountDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(command("0.95"));

        assertEquals(DialogueStep.RECONFIRMING, result.getNextStep());
        assertEquals(VoiceNextAction.RECONFIRM_INPUT, result.getNextAction());
        assertEquals(VoiceRequestedFunction.TRANSFER_AMOUNT_VALIDATION, result.getRequestedFunction());
    }

    @Test
    void rejectsMultipleAmountCandidatesWithoutReconfirming() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String draft = ambiguousAmountDraft()
                .replace("\"RECONFIRMING\"", "\"AWAITING_AMOUNT\"")
                .replace("\"RECONFIRM_INPUT\"", "\"ASK_AMOUNT\"");
        mockResponse(restTemplate, structuredResponse(draft));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(command("0.95")));

        assertEquals(ErrorCode.LLM_ANALYSIS_FAILED, exception.getErrorCode());
    }

    @Test
    void rendersTtsSsmlCardAndRequiredSlotFromTheStructuredAnalysisDraft() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        mockResponse(restTemplate, structuredResponse(validDraft()));

        VoiceTurnAnalysisResult result = client(restTemplate, API_KEY).analyze(command("0.95"));

        assertEquals("홍길동 님에게 보낼 금액을 말씀해 주세요.", result.getTtsText());
        assertEquals("<speak>홍길동 님에게 보낼 금액을 말씀해 주세요.</speak>", result.getTtsSsml());
        assertEquals(300000, result.getDisplayCard().path("amount").asInt());
        assertEquals("amount", result.getRequiredSlot().path("name").asText());
        assertEquals("홍길동", result.getDraftSummary().path("recipient").asText());
    }

    private void mockResponse(RestTemplate restTemplate, JsonNode response) {
        when(restTemplate.exchange(
                        any(URI.class),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(response));
    }

    private OpenAiVoiceTurnAnalysisClient client(RestTemplate restTemplate, String apiKey) {
        return new OpenAiVoiceTurnAnalysisClient(restTemplate, objectMapper, apiKey, MODEL, ENDPOINT);
    }

    private VoiceTurnAnalysisCommand command(String confidence) {
        return command(
                VoiceFlowType.TRANSFER,
                DialogueStep.AWAITING_RECIPIENT,
                new BigDecimal(confidence),
                DialogueInputType.VOICE);
    }

    private VoiceTurnAnalysisCommand command(
            VoiceFlowType flowType,
            DialogueStep currentStep,
            BigDecimal confidence,
            DialogueInputType inputType) {
        return command(flowType, currentStep, confidence, inputType, "홍길동에게 삼십만 원 보내줘");
    }

    private VoiceTurnAnalysisCommand generalFinanceCommand(String transcript) {
        return command(
                VoiceFlowType.GENERAL_FINANCE,
                DialogueStep.AWAITING_INPUT,
                new BigDecimal("0.95"),
                DialogueInputType.VOICE,
                transcript);
    }

    private VoiceTurnAnalysisCommand command(
            VoiceFlowType flowType,
            DialogueStep currentStep,
            BigDecimal confidence,
            DialogueInputType inputType,
            String transcript) {
        return new VoiceTurnAnalysisCommand(
                "00000000-0000-0000-0000-000000000001",
                "10000000-0000-0000-0000-000000000001",
                "20000000-0000-0000-0000-000000000001",
                flowType,
                currentStep,
                transcript,
                confidence,
                inputType);
    }

    private void assertInvalidRequest(
            OpenAiVoiceTurnAnalysisClient client,
            VoiceTurnAnalysisCommand command) {
        BusinessException exception = assertThrows(BusinessException.class, () -> client.analyze(command));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    private JsonNode structuredResponse(String draft) throws Exception {
        return objectMapper.readTree("""
                {
                  "status": "completed",
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": %s}]
                  }]
                }
                """.formatted(objectMapper.writeValueAsString(draft)));
    }

    private String validDraft() {
        return """
                {
                  "intent": "TRANSFER",
                  "slotsJson": "{\\"recipient\\":\\"홍길동\\",\\"amount\\":300000}",
                  "confidence": 0.96,
                  "nextStep": "AWAITING_AMOUNT",
                  "nextAction": "ASK_AMOUNT",
                  "requestedFunction": "TRANSFER_RECIPIENT_CANDIDATES"
                }
                """;
    }

    private String pinRequestDraft() {
        return """
                {
                  "intent": "TRANSFER",
                  "slotsJson": "{\\"recipient\\":\\"홍길동\\",\\"amount\\":300000}",
                  "confidence": 0.96,
                  "nextStep": "WAITING_FINAL_APPROVAL",
                  "nextAction": "ASK_PIN",
                  "requestedFunction": "NONE"
                }
                """;
    }

    private String billInquiryDraft() {
        return """
                {
                  "intent": "FINANCIAL_TASK",
                  "slotsJson": "{}",
                  "confidence": 0.96,
                  "nextStep": "AWAITING_INPUT",
                  "nextAction": "PRESENT_RESULT",
                  "requestedFunction": "BILL_INQUIRY"
                }
                """;
    }

    private String billPaymentDraft() {
        return billInquiryDraft().replace("BILL_INQUIRY", "BILL_PAYMENT");
    }

    private String ambiguousAmountDraft() {
        return """
                {
                  "intent": "TRANSFER",
                  "slotsJson": "{\\"amountCandidates\\":[50000,500000]}",
                  "confidence": 0.96,
                  "nextStep": "RECONFIRMING",
                  "nextAction": "RECONFIRM_INPUT",
                  "requestedFunction": "TRANSFER_AMOUNT_VALIDATION"
                }
                """;
    }
}
