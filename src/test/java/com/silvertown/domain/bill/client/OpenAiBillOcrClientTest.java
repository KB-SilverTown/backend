package com.silvertown.domain.bill.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class OpenAiBillOcrClientTest {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-5.6-terra";
    private static final String API_KEY = "test-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesStructuredBillCandidateAndUsesResponsesApiSchema() throws Exception {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(structuredResponse()));

        BillOcrCandidate result = client(restTemplate, API_KEY).analyze(new byte[] {1, 2, 3}, "image/jpeg");

        assertEquals("한국전력", result.payee());
        assertEquals(48200L, result.amount());
        assertEquals(LocalDate.of(2026, 9, 25), result.dueDate());
        assertEquals(new BigDecimal("0.98"), result.fieldConfidences().get("amount"));

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(URI.create(ENDPOINT)), eq(HttpMethod.POST), requestCaptor.capture(), eq(JsonNode.class));
        HttpEntity<?> request = requestCaptor.getValue();
        assertEquals("Bearer " + API_KEY, request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        JsonNode body = (JsonNode) request.getBody();
        assertEquals(MODEL, body.path("model").asText());
        assertFalse(body.path("store").asBoolean(true));
        assertEquals("json_schema", body.path("text").path("format").path("type").asText());
        assertFalse(body.path("text").path("format").path("schema").path("additionalProperties")
                .asBoolean(true));
        assertTrue(body.path("input").get(1).path("content").get(1).path("image_url").asText()
                .startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void mapsProviderConnectionFailuresToBillOcrError() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, API_KEY).analyze(new byte[] {1}, "image/png"));

        assertEquals(ErrorCode.BILL_OCR_FAILED, exception.getErrorCode());
    }

    @Test
    void rejectsMissingApiKeyBeforeCallingProvider() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> client(restTemplate, " ").analyze(new byte[] {1}, "image/png"));

        assertEquals(ErrorCode.LLM_NOT_CONFIGURED, exception.getErrorCode());
    }

    private OpenAiBillOcrClient client(RestTemplate restTemplate, String apiKey) {
        return new OpenAiBillOcrClient(restTemplate, objectMapper, apiKey, MODEL, ENDPOINT);
    }

    private JsonNode structuredResponse() throws Exception {
        String candidate = """
                {
                  "payee": "한국전력",
                  "amount": 48200,
                  "dueDate": "2026-09-25",
                  "paymentReference": "1234-5678",
                  "fieldConfidences": {
                    "payee": 0.97,
                    "amount": 0.98,
                    "dueDate": 0.96,
                    "paymentReference": 0.95
                  }
                }
                """;
        return objectMapper.readTree("""
                {
                  "status": "completed",
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": %s}]
                  }]
                }
                """.formatted(objectMapper.writeValueAsString(candidate)));
    }
}
