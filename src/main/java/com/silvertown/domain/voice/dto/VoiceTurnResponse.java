package com.silvertown.domain.voice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.silvertown.domain.voice.enums.DialogueStep;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Getter;

@Getter
public class VoiceTurnResponse {
    private final String sessionId;
    private final String turnId;
    private final String aiTurnId;
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

    public VoiceTurnResponse(
            String sessionId,
            String turnId,
            DialogueStep state,
            String intent,
            String requestedFunction,
            Map<String, Object> slots,
            BigDecimal confidence,
            String ttsText,
            String ttsSsml,
            JsonNode displayCard,
            JsonNode requiredSlot,
            JsonNode draftSummary,
            String nextAction
    ) {
        this(sessionId, turnId, null, state, intent, requestedFunction, slots, confidence, ttsText, ttsSsml,
                displayCard, requiredSlot, draftSummary, nextAction);
    }

    public VoiceTurnResponse(
            String sessionId,
            String turnId,
            String aiTurnId,
            DialogueStep state,
            String intent,
            String requestedFunction,
            Map<String, Object> slots,
            BigDecimal confidence,
            String ttsText,
            String ttsSsml,
            JsonNode displayCard,
            JsonNode requiredSlot,
            JsonNode draftSummary,
            String nextAction
    ) {
        this.sessionId = sessionId;
        this.turnId = turnId;
        this.aiTurnId = aiTurnId;
        this.state = state;
        this.intent = intent;
        this.requestedFunction = requestedFunction;
        this.slots = slots;
        this.confidence = confidence;
        this.ttsText = ttsText;
        this.ttsSsml = ttsSsml;
        this.displayCard = displayCard;
        this.requiredSlot = requiredSlot;
        this.draftSummary = draftSummary;
        this.nextAction = nextAction;
    }

    /** Retained for callers that do not yet supply response correlation fields. */
    public VoiceTurnResponse(
            DialogueStep state,
            String intent,
            Map<String, Object> slots,
            BigDecimal confidence,
            String ttsText,
            String ttsSsml,
            JsonNode displayCard,
            JsonNode requiredSlot,
            JsonNode draftSummary,
            String nextAction
    ) {
        this(null, null, null, state, intent, null, slots, confidence, ttsText, ttsSsml,
                displayCard, requiredSlot, draftSummary, nextAction);
    }
}
