package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Produces the safe handoff from a voice request to FE location acquisition. */
@Component
@RequiredArgsConstructor
public class MobileBranchVoiceResponseResolver {
    private static final String NEARBY_SCREEN = "MOBILE_BRANCH_NEARBY";

    private final ObjectMapper objectMapper;

    public VoiceTurnAnalysisResult resolve(VoiceTurnAnalysisResult analysis) {
        if (analysis.getRequestedFunction() != VoiceRequestedFunction.MOBILE_BRANCH_RECOMMEND) {
            return analysis;
        }

        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.put("locationRequired", true);
        String ttsText = "현재 위치를 이용해 가까운 이동점포를 찾아드릴게요. 위치 사용을 허용해 주세요.";
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                analysis.getIntent(),
                slots,
                analysis.getConfidence(),
                ttsText,
                "<speak>" + ttsText + "</speak>",
                locationRequiredCard(),
                analysis.getRequiredSlot(),
                objectMapper.valueToTree(slots),
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.MOBILE_BRANCH_RECOMMEND);
    }

    private ObjectNode locationRequiredCard() {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "MOBILE_BRANCH_LOCATION_REQUIRED");
        card.put("screenCode", NEARBY_SCREEN);
        card.put("apiPath", "/api/mobile-branches/nearby");
        card.putArray("requiredParams").add("latitude").add("longitude");
        card.putArray("actions");
        return card;
    }
}
