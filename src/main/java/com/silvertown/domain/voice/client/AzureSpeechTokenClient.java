package com.silvertown.domain.voice.client;

import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AzureSpeechTokenClient {

    private static final String SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key";
    private static final String TOKEN_PATH = "/sts/v1.0/issueToken";
    private static final String COGNITIVE_SERVICES_HOST_SUFFIX = ".cognitiveservices.azure.com";

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String subscriptionKey;
    private final String region;

    public AzureSpeechTokenClient(
            RestTemplate restTemplate,
            @Value("${azure.speech.endpoint:}") String endpoint,
            @Value("${azure.speech.key:}") String subscriptionKey,
            @Value("${azure.speech.region:}") String region
    ) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
        this.subscriptionKey = subscriptionKey;
        this.region = region;
    }

    public String issueToken() {
        URI tokenUri = tokenUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(SUBSCRIPTION_KEY_HEADER, subscriptionKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class
            );
            String token = response.getBody();
            if (token == null || token.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.SPEECH_TOKEN_ISSUANCE_FAILED);
            }
            return token;
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.SPEECH_TOKEN_ISSUANCE_FAILED);
        }
    }

    private URI tokenUri() {
        if (isBlank(endpoint) || isBlank(subscriptionKey)) {
            throw new BusinessException(ErrorCode.SPEECH_NOT_CONFIGURED);
        }
        try {
            URI configuredEndpoint = URI.create(endpoint);
            if (!isAllowedEndpoint(configuredEndpoint)) {
                throw new BusinessException(ErrorCode.SPEECH_NOT_CONFIGURED);
            }
            return configuredEndpoint.resolve(TOKEN_PATH);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SPEECH_NOT_CONFIGURED);
        }
    }

    private boolean isAllowedEndpoint(URI configuredEndpoint) {
        if (isBlank(region)
                || !configuredEndpoint.isAbsolute()
                || !"https".equalsIgnoreCase(configuredEndpoint.getScheme())
                || isBlank(configuredEndpoint.getHost())
                || configuredEndpoint.getRawUserInfo() != null
                || configuredEndpoint.getRawQuery() != null
                || configuredEndpoint.getRawFragment() != null) {
            return false;
        }

        String path = configuredEndpoint.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            return false;
        }

        String host = configuredEndpoint.getHost().toLowerCase(Locale.ROOT);
        String regionalHost = region.trim().toLowerCase(Locale.ROOT)
                + ".api.cognitive.microsoft.com";
        return host.equals(regionalHost) || host.endsWith(COGNITIVE_SERVICES_HOST_SUFFIX);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
