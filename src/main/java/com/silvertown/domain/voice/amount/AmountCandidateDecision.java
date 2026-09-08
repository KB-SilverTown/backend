package com.silvertown.domain.voice.amount;

import java.util.List;

/** The only amount decision that may cross from the Azure STT boundary into transfer processing. */
public record AmountCandidateDecision(
        AmountCandidateDecisionType type,
        Long recognizedAmount,
        List<Long> candidates,
        boolean confusionPairApplied) {

    public AmountCandidateDecision {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
