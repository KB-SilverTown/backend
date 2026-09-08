package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.voice.dto.VoiceReplayPayloadResponse;
import com.silvertown.domain.voice.dto.VoiceSessionEventRequest;
import com.silvertown.domain.voice.dto.VoiceSessionEventResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEventType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.VoiceProgressPromptFactory;
import com.silvertown.domain.voice.service.VoiceSessionEventService;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceSessionEventServiceImpl implements VoiceSessionEventService {
    private static final String AI_SPEAKER = "AI";

    private final VoiceSessionMapper voiceSessionMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final VoiceProgressPromptFactory voiceProgressPromptFactory;
    private final Clock clock;

    @Override
    @Transactional
    public VoiceSessionEventResponse handle(String userId, String sessionId, VoiceSessionEventRequest request) {
        VoiceSessionVo voiceSession = voiceSessionMapper.findOwnedByIdForUpdate(userId, sessionId);
        requireActiveGeneralClientSession(voiceSession);

        VoiceSessionEventType eventType = VoiceSessionEventType.valueOf(request.getEventType());
        return switch (eventType) {
            case INTERRUPTED -> interrupt(userId, voiceSession, request.getTurnId());
            case REPLAY -> replay(voiceSession, request.getTurnId());
        };
    }

    private VoiceSessionEventResponse interrupt(
            String userId, VoiceSessionVo voiceSession, String turnId) {
        DialogueTurnVo turn = requireActiveAiTurn(voiceSession.getSessionId(), turnId);
        if (dialogueTurnMapper.markInterrupted(turn.getTurnId()) != 1) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        voiceSessionMapper.updateStatusAndStep(
                userId,
                voiceSession.getSessionId(),
                VoiceSessionStatus.LISTENING.name(),
                voiceSession.getCurrentStep());
        return new VoiceSessionEventResponse(
                DialogueStep.valueOf(voiceSession.getCurrentStep()), turnId, null);
    }

    private VoiceSessionEventResponse replay(VoiceSessionVo voiceSession, String turnId) {
        DialogueTurnVo target = replayTargetAiTurn(voiceSession.getSessionId(), turnId);
        if (dialogueTurnMapper.incrementReplayCount(target.getTurnId()) != 1) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        VoiceReplayPayloadResponse replayPayload = voiceProgressPromptFactory.replayPayload(replaySource(target));
        return new VoiceSessionEventResponse(
                DialogueStep.valueOf(voiceSession.getCurrentStep()), null, replayPayload);
    }

    private DialogueTurnVo replaySource(DialogueTurnVo target) {
        // The replay button recaps business progress; it does not repeat the continuation confirmation.
        if (DialogueStep.valueOf(target.getStep()) != DialogueStep.AWAITING_CONTINUATION) {
            return target;
        }
        DialogueTurnVo previous = dialogueTurnMapper.findLatestBusinessAiTurn(target.getSessionId());
        if (previous == null) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        return previous;
    }

    /** REPLAY accepts the client-visible USER turn ID and resolves its stored AI response. */
    private DialogueTurnVo replayTargetAiTurn(String sessionId, String turnId) {
        DialogueTurnVo turn = dialogueTurnMapper.findBySessionIdAndTurnId(sessionId, turnId);
        if (turn != null && !AI_SPEAKER.equals(turn.getSpeaker())) {
            turn = dialogueTurnMapper.findBySessionIdAndSequenceNo(
                    sessionId, turn.getSequenceNo() + 1);
        }
        return requireActiveAiTurn(turn);
    }

    /** INTERRUPTED only invalidates the exact AI response that is being spoken. */
    private DialogueTurnVo requireActiveAiTurn(String sessionId, String turnId) {
        return requireActiveAiTurn(dialogueTurnMapper.findBySessionIdAndTurnId(sessionId, turnId));
    }

    private DialogueTurnVo requireActiveAiTurn(DialogueTurnVo turn) {
        if (turn == null || !AI_SPEAKER.equals(turn.getSpeaker()) || turn.isInterrupted()) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        return turn;
    }

    private void requireActiveGeneralClientSession(VoiceSessionVo voiceSession) {
        if (voiceSession == null) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        if (VoiceFlowType.valueOf(voiceSession.getFlowType()) != VoiceFlowType.GENERAL_FINANCE
                || SttMode.valueOf(voiceSession.getSttMode()) != SttMode.CLIENT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(voiceSession.getStatus());
        if (status == VoiceSessionStatus.CLOSED
                || status == VoiceSessionStatus.EXPIRED
                || voiceSession.getExpiresAt() == null
                || !voiceSession.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }
}
