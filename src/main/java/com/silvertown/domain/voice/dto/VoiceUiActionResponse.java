package com.silvertown.domain.voice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.silvertown.domain.voice.enums.DialogueStep;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceUiActionResponse {
    private final String sessionId;
    private final String actionId;
    private final String responseTurnId;
    private final DialogueStep state;
    private final String intent;
    private final String requestedFunction;
    private final Map<String, Object> slots;
    private final BigDecimal confidence;
    private final String ttsText;
    private final String ttsSsml;
    private final JsonNode displayCard;
    private final JsonNode requiredSlot;
    private final JsonNode draftSummary;
    private final String nextAction;
}
