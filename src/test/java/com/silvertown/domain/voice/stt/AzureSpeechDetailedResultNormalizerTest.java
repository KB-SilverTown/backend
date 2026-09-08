package com.silvertown.domain.voice.stt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AzureSpeechDetailedResultNormalizerTest {
    private final AzureSpeechDetailedResultNormalizer normalizer =
            new AzureSpeechDetailedResultNormalizer(new ObjectMapper());

    @Test
    void preservesAzureRankOrderInsteadOfSortingByConfidence() {
        AzureSpeechDetailedResult result = normalizer.normalize("오만 원", """
                {"NBest":[
                  {"Display":"오만 원","Confidence":0.91},
                  {"Display":"오십만 원","Confidence":0.99}
                ]}
                """);

        assertEquals(new BigDecimal("0.91"), result.confidence());
        assertEquals("오만 원", result.nBestAlternatives().get(0).transcript());
        assertEquals("오십만 원", result.nBestAlternatives().get(1).transcript());
    }

    @Test
    void safelyFallsBackToFinalTranscriptWhenDetailedNBestIsMissing() {
        AzureSpeechDetailedResult result = normalizer.normalize("오만 원", "{\"Display\":\"오만 원\"}");

        assertEquals("오만 원", result.transcript());
        assertNull(result.confidence());
        assertEquals(0, result.nBestAlternatives().size());
    }
}
