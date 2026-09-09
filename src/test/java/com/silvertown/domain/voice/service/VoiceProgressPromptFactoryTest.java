package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceReplayPayloadResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VoiceProgressPromptFactoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VoiceProgressPromptFactory factory = new VoiceProgressPromptFactory(objectMapper);

    @Test
    void replaysTheStoredPayloadAndUsesSeniorFriendlyWordsForNewPrompts() throws Exception {
        VoiceReplayPayloadResponse replay = factory.replayPayload(amountPrompt());

        assertEquals("김영희 님에게 송금할 금액을 말씀해 주세요.", replay.getTtsText());
        assertEquals("<speak>저장된 SSML</speak>", replay.getTtsSsml());
        assertFalse(factory.continuationQuestion().getTtsText().contains("세션"));
        assertFalse(factory.closePrompt().getTtsText().contains("세션"));
    }

    @Test
    void doesNotRewriteStoredReplayTextWhenSlotsDoNotContainRecipient() throws Exception {
        DialogueTurnVo source = amountPrompt();
        source.setExtractedSlots(objectMapper.writeValueAsString(Map.of("slots", Map.of())));

        VoiceReplayPayloadResponse replay = factory.replayPayload(source);

        assertEquals("김영희 님에게 송금할 금액을 말씀해 주세요.", replay.getTtsText());
    }

    private DialogueTurnVo amountPrompt() throws Exception {
        DialogueTurnVo turn = new DialogueTurnVo();
        turn.setSpeaker("AI");
        turn.setStep(DialogueStep.AWAITING_AMOUNT.name());
        turn.setTtsText("김영희 님에게 송금할 금액을 말씀해 주세요.");
        turn.setTtsSsml("<speak>저장된 SSML</speak>");
        turn.setDisplayCard(objectMapper.writeValueAsString(Map.of("recipient", "김영희")));
        turn.setExtractedSlots(objectMapper.writeValueAsString(Map.of(
                "slots", Map.of("recipient", "김영희"),
                "confidence", 0.95,
                "nextAction", "ASK_AMOUNT")));
        return turn;
    }
}
