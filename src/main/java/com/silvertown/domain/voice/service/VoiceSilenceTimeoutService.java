package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Applies the agreed 15-second continuation prompt before closing an idle voice session. */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceSilenceTimeoutService {
    private static final String AI_SPEAKER = "AI";
    private static final int SILENCE_TIMEOUT_MILLIS = 15_000;
    private static final Duration SILENCE_TIMEOUT = Duration.ofMillis(SILENCE_TIMEOUT_MILLIS);

    private final VoiceSessionMapper voiceSessionMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final VoiceProgressPromptFactory voiceProgressPromptFactory;
    private final VoiceSsmlRenderer voiceSsmlRenderer;
    private final VoiceTransferOrchestrator voiceTransferOrchestrator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${voice.session.silence-check-millis:1000}")
    public void handleExpiredSilence() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<VoiceSessionVo> candidates = voiceSessionMapper.findActiveInteractiveSessions(now);
        for (VoiceSessionVo candidate : candidates) {
            try {
                new TransactionTemplate(transactionManager).execute(status -> {
                    handleCandidate(candidate.getUserId(), candidate.getSessionId());
                    return null;
                });
            } catch (RuntimeException exception) {
                log.warn("Unable to process voice-session silence timeout. sessionId={}",
                        candidate.getSessionId());
            }
        }
    }

    private void handleCandidate(String userId, String sessionId) {
        VoiceSessionVo voiceSession = voiceSessionMapper.findOwnedByIdForUpdate(userId, sessionId);
        if (!isActiveInteractiveSession(voiceSession)) {
            return;
        }

        DialogueTurnVo latestTurn = dialogueTurnMapper.findLatestBySessionIdForUpdate(sessionId);
        if (!isExpiredAiPrompt(latestTurn)) {
            return;
        }
        if (dialogueTurnMapper.recordSilenceMs(latestTurn.getTurnId(), SILENCE_TIMEOUT_MILLIS) != 1) {
            return;
        }

        if (DialogueStep.valueOf(voiceSession.getCurrentStep()) == DialogueStep.AWAITING_CONTINUATION) {
            cancelUnexecutedTransfer(voiceSession);
            saveAiTurn(userId, sessionId, voiceProgressPromptFactory.closePrompt());
            voiceSessionMapper.closeOwned(
                    userId, sessionId, DialogueStep.CANCELLED.name(), LocalDateTime.now(clock));
            return;
        }

        saveAiTurn(userId, sessionId, voiceProgressPromptFactory.continuationQuestion());
        voiceSessionMapper.updateStatusAndStep(
                userId,
                sessionId,
                VoiceSessionStatus.SPEAKING.name(),
                DialogueStep.AWAITING_CONTINUATION.name());
    }

    private void cancelUnexecutedTransfer(VoiceSessionVo voiceSession) {
        if (voiceSession.getTransferId() == null) {
            return;
        }
        voiceTransferOrchestrator.cancelUnexecuted(
                UUID.fromString(voiceSession.getUserId()), UUID.fromString(voiceSession.getTransferId()));
    }

    private boolean isActiveInteractiveSession(VoiceSessionVo voiceSession) {
        if (voiceSession == null || voiceSession.getExpiresAt() == null) {
            return false;
        }
        return (VoiceSessionStatus.LISTENING.name().equals(voiceSession.getStatus())
                        || VoiceSessionStatus.SPEAKING.name().equals(voiceSession.getStatus()))
                && voiceSession.getExpiresAt().isAfter(LocalDateTime.now(clock));
    }

    private boolean isExpiredAiPrompt(DialogueTurnVo turn) {
        return turn != null
                && AI_SPEAKER.equals(turn.getSpeaker())
                && !turn.isInterrupted()
                && turn.getSilenceMs() == 0
                && turn.getCreatedAt() != null
                && !turn.getCreatedAt().plus(SILENCE_TIMEOUT).isAfter(LocalDateTime.now(clock));
    }

    private void saveAiTurn(String userId, String sessionId, VoiceTurnAnalysisResult analysis) {
        DialogueTurnVo turn = new DialogueTurnVo();
        turn.setTurnId(UUID.randomUUID().toString());
        turn.setSessionId(sessionId);
        turn.setSequenceNo(dialogueTurnMapper.findNextSequenceNo(sessionId));
        turn.setSpeaker(AI_SPEAKER);
        turn.setStep(analysis.getNextStep().name());
        turn.setIntent(analysis.getIntent().name());
        turn.setTtsText(analysis.getTtsText());
        turn.setTtsSsml(voiceSsmlRenderer.render(userId, analysis.getTtsText()));
        turn.setDisplayCard(writeJson(analysis.getDisplayCard()));
        turn.setExtractedSlots(writeStoredResponse(analysis));
        turn.setSilenceMs(0);
        turn.setReplayCount(0);
        turn.setInterrupted(false);
        dialogueTurnMapper.insert(turn);
    }

    private String writeStoredResponse(VoiceTurnAnalysisResult analysis) {
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set("slots", objectMapper.valueToTree(analysis.getSlots()));
        stored.set("confidence", objectMapper.valueToTree(analysis.getConfidence()));
        stored.put("nextAction", analysis.getNextAction().name());
        stored.set("requiredSlot", analysis.getRequiredSlot());
        stored.set("draftSummary", analysis.getDraftSummary());
        return writeJson(stored);
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
