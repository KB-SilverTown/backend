package com.silvertown.domain.voice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AzureSpeechTokenClientTest {

    @Test
    void issuesTokenWithoutExposingSubscriptionKeyInResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        AzureSpeechTokenClient client = new AzureSpeechTokenClient(
                restTemplate, "https://koreacentral.api.cognitive.microsoft.com/",
                "subscription-key", "koreacentral");
        server.expect(once(), requestTo(
                        "https://koreacentral.api.cognitive.microsoft.com/sts/v1.0/issueToken"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Ocp-Apim-Subscription-Key", "subscription-key"))
                .andRespond(withSuccess("short-lived-token", MediaType.TEXT_PLAIN));

        assertEquals("short-lived-token", client.issueToken());
        server.verify();
    }

    @Test
    void throwsConfigurationErrorWhenEndpointIsMissing() {
        AzureSpeechTokenClient client = new AzureSpeechTokenClient(
                new RestTemplate(), "", "subscription-key", "koreacentral");

        BusinessException exception = assertThrows(BusinessException.class, client::issueToken);

        assertEquals(ErrorCode.SPEECH_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void convertsAzureFailureToStandardBusinessException() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        AzureSpeechTokenClient client = new AzureSpeechTokenClient(
                restTemplate, "https://koreacentral.api.cognitive.microsoft.com",
                "subscription-key", "koreacentral");
        server.expect(once(), requestTo(
                        "https://koreacentral.api.cognitive.microsoft.com/sts/v1.0/issueToken"))
                .andRespond(withServerError());

        BusinessException exception = assertThrows(BusinessException.class, client::issueToken);

        assertEquals(ErrorCode.SPEECH_TOKEN_ISSUANCE_FAILED, exception.getErrorCode());
        server.verify();
    }

    @Test
    void rejectsHttpEndpointBeforeSendingSubscriptionKey() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        AzureSpeechTokenClient client = new AzureSpeechTokenClient(
                restTemplate, "http://koreacentral.api.cognitive.microsoft.com",
                "subscription-key", "koreacentral");

        BusinessException exception = assertThrows(BusinessException.class, client::issueToken);

        assertEquals(ErrorCode.SPEECH_NOT_CONFIGURED, exception.getErrorCode());
        Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsEndpointOutsideAzureSpeechHostsBeforeSendingSubscriptionKey() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        AzureSpeechTokenClient client = new AzureSpeechTokenClient(
                restTemplate, "https://example.com", "subscription-key", "koreacentral");

        BusinessException exception = assertThrows(BusinessException.class, client::issueToken);

        assertEquals(ErrorCode.SPEECH_NOT_CONFIGURED, exception.getErrorCode());
        Mockito.verifyNoInteractions(restTemplate);
    }
}
