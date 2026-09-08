package com.silvertown.domain.voice.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in smoke test against a running local Tomcat instance and Azure Speech.
 *
 * <p>This test intentionally leaves its generated local account in place. It never writes a JWT,
 * Speech token, secret, or provider error payload to test output.</p>
 */
@EnabledIfSystemProperty(named = "runSpeechTokenSmoke", matches = "true")
class SpeechTokenLiveSmokeTest {
    private static final String BASE_URL = normalizeBaseUrl(
            System.getProperty("speechSmokeBaseUrl", "localhost:8080"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final HttpClient LOOPBACK_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .proxy(ProxySelector.of(null))
            .build();

    @Test
    void signsUpLogsInIssuesAndUsesAzureSpeechToken() throws Exception {
        String loginId = createDedicatedSmokeAccount();
        assertTrue(loginId.startsWith("speech-smoke-"));
        assertFalse(loginId.startsWith("local-"));

        HttpResponse<String> loginResponse = postJson("/api/auth/login", loginPayload(loginId), null);
        assertEquals(200, loginResponse.statusCode());
        String accessToken = OBJECT_MAPPER.readTree(loginResponse.body()).path("accessToken").asText();
        assertFalse(accessToken.isBlank());

        HttpResponse<String> tokenResponse = postJson("/api/voice/speech-token", null, accessToken);
        assertEquals(200, tokenResponse.statusCode());
        JsonNode tokenBody = OBJECT_MAPPER.readTree(tokenResponse.body());
        String speechToken = tokenBody.path("token").asText();
        String region = tokenBody.path("region").asText();
        String expiresAt = tokenBody.path("expiresAt").asText();

        assertFalse(speechToken.isBlank());
        assertEquals("koreacentral", region);
        assertTrue(OffsetDateTime.parse(expiresAt).isAfter(OffsetDateTime.now()));

        synthesizeWithIssuedToken(speechToken, region);
        verifiesLiveVoiceTurnAnalysis(accessToken);
    }

    private String createDedicatedSmokeAccount() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String loginId = "speech-smoke-" + suffix;
            HttpResponse<String> response = postJson("/api/auth/signup", signUpPayload(loginId, suffix), null);
            if (response.statusCode() == 201) {
                return loginId;
            }
            if (response.statusCode() != 409) {
                throw new AssertionError("Smoke account signup did not succeed.");
            }
        }
        throw new AssertionError("Unable to create a unique smoke account.");
    }

    private HttpResponse<String> postJson(String path, JsonNode payload, String accessToken) throws Exception {
        URI endpoint = URI.create(BASE_URL + path);
        validateTargetUri(endpoint);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        payload == null ? "" : OBJECT_MAPPER.writeValueAsString(payload)));
        if (accessToken != null) {
            requireSecureAuthorizationTransport(endpoint);
            request.header("Authorization", "Bearer " + accessToken);
        }
        return selectHttpClient(endpoint).send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String normalizeBaseUrl(String configuredBaseUrl) {
        String value = configuredBaseUrl == null ? "" : configuredBaseUrl.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("speechSmokeBaseUrl must not be blank.");
        }
        if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            value = "http://" + value;
        }

        URI uri = URI.create(value.replaceAll("/+$", ""));
        validateTargetUri(uri);
        return uri.toString();
    }

    private static void requireSecureAuthorizationTransport(URI uri) {
        if (!isLoopback(uri) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "JWT authorization is allowed only for loopback HTTP or HTTPS targets.");
        }
    }

    private static void validateTargetUri(URI uri) {
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("speechSmokeBaseUrl must include a host.");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("speechSmokeBaseUrl must use HTTP or HTTPS.");
        }
        if (!isLoopback(uri) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Non-loopback speechSmokeBaseUrl targets must use HTTPS.");
        }
    }

    private static boolean isLoopback(URI uri) {
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    static HttpClient selectHttpClient(URI uri) {
        return isLoopback(uri) ? LOOPBACK_HTTP_CLIENT : HTTP_CLIENT;
    }

    private JsonNode loginPayload(String loginId) {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("loginId", loginId);
        request.put("password", "speech-smoke-password-1");
        return request;
    }

    private JsonNode signUpPayload(String loginId, String suffix) {
        String numericSuffix = suffix.replaceAll("[a-f]", "1");
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("loginId", loginId);
        request.put("password", "speech-smoke-password-1");
        request.put("name", "음성스모크");
        request.put("residentRegistrationNumber", "900101-1" + numericSuffix.substring(0, 6));
        request.put("gender", "MALE");
        request.put("postalCode", "06234");
        request.put("address", "서울특별시 강남구 테헤란로 1");
        request.put("detailAddress", "101호");
        request.put("bankCode", "004");
        request.put("accountNumber", "1234" + suffix);
        request.put("phone", "010" + numericSuffix.substring(0, 8));

        ObjectNode emergencyContact = request.putObject("emergencyContact");
        emergencyContact.put("name", "스모크보호자");
        emergencyContact.put("phone", "011" + numericSuffix.substring(2, 10));
        emergencyContact.put("relationship", "자녀");

        ArrayNode consents = request.putArray("consents");
        addRequiredConsent(consents, "TERMS_OF_SERVICE");
        addRequiredConsent(consents, "PRIVACY_COLLECTION");
        addRequiredConsent(consents, "MYDATA_FINANCIAL");
        addRequiredConsent(consents, "AI_VOICE_DATA");
        return request;
    }

    private void addRequiredConsent(ArrayNode consents, String type) {
        ObjectNode consent = consents.addObject();
        consent.put("type", type);
        consent.put("agreed", true);
        consent.put("documentVersion", "v1");
    }

    private void synthesizeWithIssuedToken(String speechToken, String region) throws Exception {
        Path audioFile = Files.createTempFile("speech-token-smoke-", ".wav");
        try (SpeechConfig speechConfig = SpeechConfig.fromAuthorizationToken(speechToken, region);
                AudioConfig audioConfig = AudioConfig.fromWavFileOutput(audioFile.toString());
                SpeechSynthesizer synthesizer = new SpeechSynthesizer(speechConfig, audioConfig)) {
            SpeechSynthesisResult result = synthesizer.SpeakTextAsync("음성 토큰 확인").get(30, TimeUnit.SECONDS);
            try {
                assertNotNull(result);
                assertEquals(ResultReason.SynthesizingAudioCompleted, result.getReason());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
        } finally {
            Files.deleteIfExists(audioFile);
        }
    }

    private void verifiesLiveVoiceTurnAnalysis(String accessToken) throws Exception {
        ObjectNode sessionPayload = OBJECT_MAPPER.createObjectNode();
        sessionPayload.put("entryPoint", "GENERAL_FINANCE");
        HttpResponse<String> sessionResponse = postJson("/api/voice/sessions", sessionPayload, accessToken);
        assertEquals(201, sessionResponse.statusCode());
        String sessionId = OBJECT_MAPPER.readTree(sessionResponse.body()).path("sessionId").asText();
        assertFalse(sessionId.isBlank());

        ObjectNode turnPayload = OBJECT_MAPPER.createObjectNode();
        turnPayload.put("turnId", UUID.randomUUID().toString());
        turnPayload.put("transcript", "계좌 잔액을 알려주세요");
        turnPayload.put("sttConfidence", 0.95);
        turnPayload.put("inputType", "VOICE");
        HttpResponse<String> turnResponse = postJson(
                "/api/voice/sessions/" + sessionId + "/turns", turnPayload, accessToken);
        assertEquals(200, turnResponse.statusCode());
        JsonNode response = OBJECT_MAPPER.readTree(turnResponse.body());
        assertFalse(response.path("ttsText").asText().isBlank());
        assertFalse(response.path("ttsSsml").asText().isBlank());
        assertFalse(response.path("state").asText().isBlank());
    }

}
