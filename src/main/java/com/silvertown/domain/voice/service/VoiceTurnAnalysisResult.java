package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class VoiceTurnAnalysisResult {
    private final DialogueStep nextStep;
    private final VoiceIntent intent;
    private final Map<String, Object> slots;
    private final BigDecimal confidence;
    private final String ttsText;
    private final String ttsSsml;
    private final JsonNode displayCard;
    private final JsonNode requiredSlot;
    private final JsonNode draftSummary;
    private final VoiceNextAction nextAction;
    private final VoiceRequestedFunction requestedFunction;
}
