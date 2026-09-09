package com.silvertown.domain.voice.stt;

import java.math.BigDecimal;
import java.util.List;

/**
 * Provider-independent representation of one Azure Speech FINAL result.
 *
 * <p>N-best order is Azure's recognition rank order. It must not be sorted by confidence because
 * the first alternative is the provider's selected transcription.</p>
 */
public record AzureSpeechDetailedResult(
        String transcript, BigDecimal confidence, List<AzureSpeechNBestAlternative> nBestAlternatives) {

    public AzureSpeechDetailedResult {
        nBestAlternatives = nBestAlternatives == null ? List.of() : List.copyOf(nBestAlternatives);
    }
}
