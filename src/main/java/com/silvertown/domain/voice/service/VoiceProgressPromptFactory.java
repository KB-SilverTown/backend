package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceReplayPayloadResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Creates senior-friendly prompts from already stored, verified dialogue data. */
@Component
@RequiredArgsConstructor
public class VoiceProgressPromptFactory {
    private static final String AI_SPEAKER = "AI";
    private static final String SLOTS_FIELD = "slots";
    private static final String CONFIDENCE_FIELD = "confidence";
    private static final String NEXT_ACTION_FIELD = "nextAction";
    private static final String REQUIRED_SLOT_FIELD = "requiredSlot";
    private static final String DRAFT_SUMMARY_FIELD = "draftSummary";

    private final ObjectMapper objectMapper;

    public VoiceReplayPayloadResponse replayPayload(DialogueTurnVo source) {
        if (!AI_SPEAKER.equals(source.getSpeaker())) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        return new VoiceReplayPayloadResponse(
                source.getTtsText(), source.getTtsSsml(), readJson(source.getDisplayCard()));
    }

    public VoiceTurnAnalysisResult continuationQuestion() {
        String text = "잠시 기다리셨네요. 계속 도와드릴까요? 네 또는 아니요로 말씀해 주세요.";
        return analysis(
                DialogueStep.AWAITING_CONTINUATION,
                VoiceIntent.UNKNOWN,
                Map.of(),
                BigDecimal.ZERO,
                text,
                null,
                null,
                null,
                VoiceNextAction.ASK_CONTINUATION);
    }

    public VoiceTurnAnalysisResult resumePrompt(DialogueTurnVo source) {
        StoredTurn stored = storedTurn(source);
        DialogueStep step = DialogueStep.valueOf(source.getStep());
        String text = "좋아요. " + progressText(step, stored.slots(), source.getTtsText());
        return analysis(
                step,
                voiceIntent(source.getIntent()),
                stored.slots(),
                stored.confidence(),
                text,
                readJson(source.getDisplayCard()),
                stored.requiredSlot(),
                stored.draftSummary(),
                nextAction(stored.nextAction(), step));
    }

    public VoiceTurnAnalysisResult clarifyContinuation() {
        String text = "계속 도와드릴까요? 네 또는 아니요로 말씀해 주세요.";
        return analysis(
                DialogueStep.AWAITING_CONTINUATION,
                VoiceIntent.UNKNOWN,
                Map.of(),
                BigDecimal.ZERO,
                text,
                null,
                null,
                null,
                VoiceNextAction.ASK_CONTINUATION);
    }

    public VoiceTurnAnalysisResult closePrompt() {
        String text = "알겠습니다. 지금 대화를 마칠게요. 필요하시면 다시 시작해 주세요.";
        return analysis(
                DialogueStep.CANCELLED,
                VoiceIntent.UNKNOWN,
                Map.of(),
                BigDecimal.ZERO,
                text,
                null,
                null,
                null,
                VoiceNextAction.END_SESSION);
    }

    private VoiceTurnAnalysisResult analysis(
            DialogueStep step,
            VoiceIntent intent,
            Map<String, Object> slots,
            BigDecimal confidence,
            String ttsText,
            JsonNode displayCard,
            JsonNode requiredSlot,
            JsonNode draftSummary,
            VoiceNextAction nextAction) {
        return new VoiceTurnAnalysisResult(
                step,
                intent,
                slots,
                confidence,
                ttsText,
                toSsml(ttsText),
                displayCard,
                requiredSlot,
                draftSummary,
                nextAction,
                VoiceRequestedFunction.NONE);
    }

    private String progressText(DialogueStep step, Map<String, Object> slots, String fallback) {
        String recipient = stringSlot(slots, "recipient");
        return switch (step) {
            case AWAITING_RECIPIENT -> "돈 보내기를 진행하고 있어요. 돈을 받을 분을 다시 말씀해 주세요.";
            case AWAITING_AMOUNT -> recipient == null
                    ? "돈 보내기를 진행하고 있어요. 보낼 금액을 다시 말씀해 주세요."
                    : recipient + "님께 돈 보내기를 진행하고 있어요. 보낼 금액을 다시 말씀해 주세요.";
            case RECONFIRMING -> "말씀해 주신 내용을 다시 확인하고 있어요. 화면을 보시고 맞는지 말씀해 주세요.";
            case RISK_CHECK -> "안전을 위해 내용을 확인하고 있어요. 잠시만 기다려 주세요.";
            case WAITING_FINAL_APPROVAL -> "돈 보내기 내용을 확인하고 있어요. 화면의 내용을 확인해 주세요.";
            case HELD -> "안전을 위해 돈 보내기를 잠시 멈췄어요. 화면의 안내를 확인해 주세요.";
            default -> friendly(fallback);
        };
    }

    private String friendly(String value) {
        if (value == null || value.isBlank()) {
            return "무엇을 도와드릴까요?";
        }
        return value.replace("송금", "돈 보내기")
                .replace("수취인", "돈을 받을 분")
                .replace("세션", "대화");
    }

    private String stringSlot(Map<String, Object> slots, String name) {
        Object value = slots.get(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private VoiceIntent voiceIntent(String value) {
        try {
            return value == null ? VoiceIntent.UNKNOWN : VoiceIntent.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return VoiceIntent.UNKNOWN;
        }
    }

    private VoiceNextAction nextAction(String value, DialogueStep step) {
        try {
            VoiceNextAction candidate = VoiceNextAction.valueOf(value);
            return candidate.supports(step) ? candidate : defaultNextAction(step);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return defaultNextAction(step);
        }
    }

    private VoiceNextAction defaultNextAction(DialogueStep step) {
        return switch (step) {
            case AWAITING_RECIPIENT -> VoiceNextAction.ASK_RECIPIENT;
            case AWAITING_AMOUNT -> VoiceNextAction.ASK_AMOUNT;
            case RECONFIRMING -> VoiceNextAction.RECONFIRM_INPUT;
            case WAITING_FINAL_APPROVAL -> VoiceNextAction.ASK_FINAL_APPROVAL;
            case HELD -> VoiceNextAction.WAIT_GUARDIAN_VERIFICATION;
            case RISK_CHECK -> VoiceNextAction.NONE;
            default -> VoiceNextAction.REASK_INPUT;
        };
    }

    private StoredTurn storedTurn(DialogueTurnVo source) {
        if (!AI_SPEAKER.equals(source.getSpeaker())) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        try {
            JsonNode stored = source.getExtractedSlots() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(source.getExtractedSlots());
            JsonNode slots = stored.path(SLOTS_FIELD);
            return new StoredTurn(
                    slots.isObject()
                            ? objectMapper.convertValue(slots, new TypeReference<Map<String, Object>>() {})
                            : Map.of(),
                    stored.path(CONFIDENCE_FIELD).isNumber()
                            ? stored.path(CONFIDENCE_FIELD).decimalValue()
                            : BigDecimal.ZERO,
                    stored.path(NEXT_ACTION_FIELD).asText(null),
                    stored.get(REQUIRED_SLOT_FIELD),
                    stored.get(DRAFT_SUMMARY_FIELD));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toSsml(String text) {
        return "<speak>" + text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;") + "</speak>";
    }

    private record StoredTurn(
            Map<String, Object> slots,
            BigDecimal confidence,
            String nextAction,
            JsonNode requiredSlot,
            JsonNode draftSummary) {}
}
