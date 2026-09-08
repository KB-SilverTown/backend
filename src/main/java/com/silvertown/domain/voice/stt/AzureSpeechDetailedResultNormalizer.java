package com.silvertown.domain.voice.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts Azure Speech detailed JSON into the small internal STT contract used by finance logic. */
@Component
public class AzureSpeechDetailedResultNormalizer {
    private static final String NBEST_FIELD = "NBest";
    private static final String DISPLAY_FIELD = "Display";
    private static final String CONFIDENCE_FIELD = "Confidence";

    private final ObjectMapper objectMapper;

    public AzureSpeechDetailedResultNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AzureSpeechDetailedResult normalize(String finalTranscript, String detailedJson) {
        if (isBlank(finalTranscript)) {
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        }

        if (isBlank(detailedJson)) {
            return new AzureSpeechDetailedResult(finalTranscript, null, List.of());
        }

        try {
            JsonNode detail = objectMapper.readTree(detailedJson);
            JsonNode alternatives = detail.path(NBEST_FIELD);
            if (!alternatives.isArray() || alternatives.isEmpty()) {
                return new AzureSpeechDetailedResult(finalTranscript, null, List.of());
            }

            List<AzureSpeechNBestAlternative> normalized = new ArrayList<>();
            for (JsonNode alternative : alternatives) {
                String transcript = alternative.path(DISPLAY_FIELD).asText(null);
                if (!isBlank(transcript)) {
                    normalized.add(new AzureSpeechNBestAlternative(
                            transcript, decimalInRange(alternative.get(CONFIDENCE_FIELD))));
                }
            }
            BigDecimal rankOneConfidence = normalized.isEmpty()
                    ? null
                    : normalized.get(0).confidence();
            return new AzureSpeechDetailedResult(finalTranscript, rankOneConfidence, normalized);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        }
    }

    private BigDecimal decimalInRange(JsonNode value) {
        if (value == null || !value.isNumber()) {
            return null;
        }
        BigDecimal confidence = value.decimalValue();
        return confidence.compareTo(BigDecimal.ZERO) >= 0 && confidence.compareTo(BigDecimal.ONE) <= 0
                ? confidence
                : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
