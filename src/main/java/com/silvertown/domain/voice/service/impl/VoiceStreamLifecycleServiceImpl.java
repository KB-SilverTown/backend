package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.VoiceStreamLifecycleService;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceStreamLifecycleServiceImpl implements VoiceStreamLifecycleService {
    private final VoiceSessionMapper voiceSessionMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final Clock clock;

    @Override
    @Transactional
    public long claimInputTurn(String userId, String sessionId, String inputTurnId) {
        requireTurnId(inputTurnId);
        LocalDateTime now = LocalDateTime.now(clock);
        VoiceSessionVo session = findOwnedForUpdate(userId, sessionId);
        requireBackendTransferSession(session, now);
        requireStatus(session, VoiceSessionStatus.LISTENING);
        requireNoActiveTurns(session);
        requireUpdated(voiceSessionMapper.claimStreamInputTurn(userId, sessionId, inputTurnId, now));
        return session.getLifecycleGeneration();
    }

    @Override
    @Transactional
    public void beginFinalProcessing(
            String userId, String sessionId, String inputTurnId, long lifecycleGeneration) {
        requireTurnId(inputTurnId);
        LocalDateTime now = LocalDateTime.now(clock);
        VoiceSessionVo session = findOwnedForUpdate(userId, sessionId);
        requireBackendTransferSession(session, now);
        requireStatus(session, VoiceSessionStatus.LISTENING);
        requireActiveInput(session, inputTurnId, lifecycleGeneration);
        requireUpdated(voiceSessionMapper.beginStreamFinalProcessing(
                userId, sessionId, inputTurnId, lifecycleGeneration, now));
    }

    @Override
    @Transactional
    public void completeAiTurn(
            String userId,
            String sessionId,
            String inputTurnId,
            long lifecycleGeneration,
            String aiTurnId) {
        requireTurnId(inputTurnId);
        requireTurnId(aiTurnId);
        LocalDateTime now = LocalDateTime.now(clock);
        VoiceSessionVo session = findOwnedForUpdate(userId, sessionId);
        requireBackendTransferSession(session, now);
        requireStatus(session, VoiceSessionStatus.PROCESSING);
        requireActiveInput(session, inputTurnId, lifecycleGeneration);
        if (session.getActiveAiTurnId() != null) {
            throw turnConflict();
        }
        requireUpdated(voiceSessionMapper.completeStreamTurnWithAi(
                userId, sessionId, inputTurnId, lifecycleGeneration, aiTurnId, now));
    }

    @Override
    @Transactional
    public void interruptAiTts(
            String userId, String sessionId, String interruptedAiTurnId, long lifecycleGeneration) {
        requireTurnId(interruptedAiTurnId);
        LocalDateTime now = LocalDateTime.now(clock);
        VoiceSessionVo session = findOwnedForUpdate(userId, sessionId);
        requireBackendTransferSession(session, now);
        requireStatus(session, VoiceSessionStatus.SPEAKING);
        if (!Objects.equals(interruptedAiTurnId, session.getActiveAiTurnId())
                || session.getActiveInputTurnId() != null
                || session.getLifecycleGeneration() != lifecycleGeneration) {
            throw turnConflict();
        }
        requireUpdated(dialogueTurnMapper.markInterrupted(interruptedAiTurnId));
        requireUpdated(voiceSessionMapper.interruptActiveAiTurn(
                userId, sessionId, interruptedAiTurnId, lifecycleGeneration, now));
    }

    @Override
    @Transactional
    public void cancelInputStream(
            String userId, String sessionId, String inputTurnId, long lifecycleGeneration) {
        requireTurnId(inputTurnId);
        LocalDateTime now = LocalDateTime.now(clock);
        VoiceSessionVo session = findOwnedForUpdate(userId, sessionId);
        requireBackendTransferSession(session, now);
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(session.getStatus());
        if (status != VoiceSessionStatus.LISTENING && status != VoiceSessionStatus.PROCESSING) {
            throw turnConflict();
        }
        requireActiveInput(session, inputTurnId, lifecycleGeneration);
        if (session.getActiveAiTurnId() != null) {
            throw turnConflict();
        }
        requireUpdated(voiceSessionMapper.cancelActiveInputTurn(
                userId, sessionId, inputTurnId, lifecycleGeneration, now));
    }

    private VoiceSessionVo findOwnedForUpdate(String userId, String sessionId) {
        try {
            VoiceSessionVo session = voiceSessionMapper.findOwnedByIdForUpdate(userId, sessionId);
            if (session == null) {
                throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
            }
            return session;
        } catch (RuntimeException exception) {
            if (isNoWaitLockFailure(exception)) {
                throw turnConflict();
            }
            throw exception;
        }
    }

    private void requireBackendTransferSession(VoiceSessionVo session, LocalDateTime now) {
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(session.getStatus());
        if (status == VoiceSessionStatus.CLOSED
                || status == VoiceSessionStatus.EXPIRED
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(now)
                || VoiceFlowType.valueOf(session.getFlowType()) != VoiceFlowType.TRANSFER
                || SttMode.valueOf(session.getSttMode()) != SttMode.BACKEND_STREAM) {
            throw turnConflict();
        }
    }

    private void requireStatus(VoiceSessionVo session, VoiceSessionStatus expected) {
        if (VoiceSessionStatus.valueOf(session.getStatus()) != expected) {
            throw turnConflict();
        }
    }

    private void requireNoActiveTurns(VoiceSessionVo session) {
        if (session.getActiveInputTurnId() != null || session.getActiveAiTurnId() != null) {
            throw turnConflict();
        }
    }

    private void requireActiveInput(
            VoiceSessionVo session, String inputTurnId, long lifecycleGeneration) {
        if (!Objects.equals(inputTurnId, session.getActiveInputTurnId())
                || session.getLifecycleGeneration() != lifecycleGeneration) {
            throw turnConflict();
        }
    }

    private void requireUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw turnConflict();
        }
    }

    private void requireTurnId(String turnId) {
        if (turnId == null || !turnId.matches(VoiceIdentifierPattern.CANONICAL_UUID_REGEX)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private BusinessException turnConflict() {
        return new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
    }

    private boolean isNoWaitLockFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof CannotAcquireLockException) {
                return true;
            }
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == 3572) {
                return true;
            }
        }
        return false;
    }
}
