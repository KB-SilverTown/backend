package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import org.junit.jupiter.api.Test;

class VoiceGuidanceTemplateRendererTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VoiceGuidanceTemplateRenderer renderer = new VoiceGuidanceTemplateRenderer();

    @Test
    void rendersRecipientConfirmationWithoutChangingTheCandidateValue() {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "RECIPIENT_CANDIDATES");
        card.putArray("items").addObject().put("displayName", "김영희");

        assertEquals("받는 분은 김영희님입니다. 맞으면 네라고 말씀해 주세요.",
                renderer.render("김영희님에게 돈을 보내시려는 게 맞을까요?", card, VoiceGuidanceMode.SUPPORT));
        assertEquals("김영희님이 맞을까요?",
                renderer.render("김영희님에게 돈을 보내시려는 게 맞을까요?", card, VoiceGuidanceMode.COMPACT));
    }

    @Test
    void rendersReadbackWithTheSameRecipientAndAmount() {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "TRANSFER_READBACK");
        card.put("recipientName", "김영희");
        card.put("amount", 300000L);

        assertEquals("받는 분은 김영희님입니다. 금액은 300,000원입니다. 돈을 보내시려면 네라고 말씀해 주세요.",
                renderer.render("기존 read-back", card, VoiceGuidanceMode.SUPPORT));
        assertEquals("김영희님에게 300,000원을 보내시겠어요?",
                renderer.render("기존 read-back", card, VoiceGuidanceMode.COMPACT));
    }
}
