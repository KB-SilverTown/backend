package com.silvertown.domain.voice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.account.vo.BankAccount;
import com.silvertown.domain.voice.dto.VoiceReplayPayloadResponse;
import com.silvertown.domain.voice.adaptation.VoiceAdaptationSessionStateStore;
import com.silvertown.domain.voice.adaptation.VoiceAdaptationPolicy;
import com.silvertown.domain.voice.dto.VoiceSessionCreateRequest;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceSessionNavigationResponse;
import com.silvertown.domain.voice.dto.VoiceSessionResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceSessionNavigationScreenCode;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.VoiceSessionService;
import com.silvertown.domain.voice.service.VoiceGuidanceSettingsService;
import com.silvertown.domain.voice.service.VoiceSessionPromptProvider;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceSessionServiceImpl implements VoiceSessionService {
    private static final Duration SESSION_IDLE_TTL = Duration.ofMinutes(15);
    private static final String AI_SPEAKER = "AI";

    private final VoiceSessionMapper voiceSessionMapper;
    private final AccountMapper accountMapper;
    private final VoiceTransferOrchestrator voiceTransferOrchestrator;
    private final VoiceInteractionCardMapper voiceInteractionCardMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final VoiceSessionPromptProvider voiceSessionPromptProvider;
    private final VoiceSsmlRenderer voiceSsmlRenderer;
    private final VoiceAdaptationSessionStateStore voiceAdaptationSessionStateStore;
    private final VoiceGuidanceSettingsService voiceGuidanceSettingsService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Compatibility constructor retained for focused unit tests that do not load Spring. */
    public VoiceSessionServiceImpl(
            VoiceSessionMapper voiceSessionMapper,
            AccountMapper accountMapper,
            VoiceTransferOrchestrator voiceTransferOrchestrator,
            VoiceInteractionCardMapper voiceInteractionCardMapper,
            DialogueTurnMapper dialogueTurnMapper,
            VoiceSessionPromptProvider voiceSessionPromptProvider,
            VoiceSsmlRenderer voiceSsmlRenderer,
            ObjectMapper objectMapper,
            Clock clock) {
        this(voiceSessionMapper, accountMapper, voiceTransferOrchestrator, voiceInteractionCardMapper,
                dialogueTurnMapper, voiceSessionPromptProvider, voiceSsmlRenderer,
                new VoiceAdaptationSessionStateStore(new VoiceAdaptationPolicy()),
                new VoiceGuidanceSettingsService(null), objectMapper, clock);
    }

    /**
     * Creates a voice session and generates its initial prompt.
     *
     * @param userId  the user who owns the session
     * @param request the requested session configuration
     * @return the newly created voice session details
     */
    @Override
    @Transactional
    public VoiceSessionResponse create(String userId, VoiceSessionCreateRequest request) {
        VoiceSessionEntryPoint entryPoint = requireEntryPoint(request);
        String fromAccountId = requireTransferAccount(userId, entryPoint);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(SESSION_IDLE_TTL);
        VoiceSessionVo voiceSession = new VoiceSessionVo();
        voiceSession.setSessionId(UUID.randomUUID().toString());
        voiceSession.setUserId(userId);
        voiceSession.setFromAccountId(fromAccountId);
        voiceSession.setStatus(VoiceSessionStatus.LISTENING.name());
        voiceSession.setCurrentStep(DialogueStep.AWAITING_INPUT.name());
        voiceSession.setFlowType(entryPoint.getFlowType().name());
        voiceSession.setSttMode(entryPoint.getSttMode().name());
        voiceSession.setEntryPoint(entryPoint.name());
        voiceSession.setStartedAt(now);
        voiceSession.setExpiresAt(expiresAt);
        voiceSessionMapper.insert(voiceSession);
        voiceAdaptationSessionStateStore.initialize(
                voiceSession.getSessionId(), DialogueStep.AWAITING_INPUT,
                voiceGuidanceSettingsService.initialMode(userId));
        String firstPrompt = voiceSessionPromptProvider.firstPromptFor(entryPoint);
        saveFirstPrompt(userId, voiceSession.getSessionId(), firstPrompt);

        return new VoiceSessionResponse(
                voiceSession.getSessionId(),
                entryPoint,
                VoiceSessionStatus.LISTENING,
                DialogueStep.AWAITING_INPUT,
                entryPoint.getFlowType(),
                entryPoint.getSttMode(),
                toOffsetDateTime(expiresAt),
                firstPrompt,
                navigationFor(entryPoint, voiceSession.getSessionId()));
    }

    @Override
    @Transactional
    public VoiceSessionDetailResponse get(String userId, String sessionId) {
        return toDetailResponse(findOwnedAndExpireIfNeeded(userId, sessionId));
    }

    /**
     * Closes an active voice session and performs associated session cleanup.
     *
     * @param userId    the user who owns the session
     * @param sessionId the session to close
     * @return the updated voice-session details
     */
    @Override
    @Transactional
    public VoiceSessionDetailResponse close(String userId, String sessionId) {
        VoiceSessionVo voiceSession = findOwnedAndExpireIfNeeded(userId, sessionId);
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(voiceSession.getStatus());

        if (status != VoiceSessionStatus.CLOSED && status != VoiceSessionStatus.EXPIRED) {
            cancelUnexecutedTransfer(voiceSession);
            voiceSessionMapper.closeOwned(
                    userId,
                    sessionId,
                    voiceSession.getCurrentStep(),
                    LocalDateTime.now(clock));
            voiceInteractionCardMapper.deactivateActiveBySessionId(sessionId);
            voiceGuidanceSettingsService.completeSession(
                    userId, voiceAdaptationSessionStateStore.stateOf(sessionId));
            voiceAdaptationSessionStateStore.clear(sessionId);
            voiceSession = findOwnedAndExpireIfNeeded(userId, sessionId);
        }

        voiceAdaptationSessionStateStore.clear(sessionId);

        return toDetailResponse(voiceSession);
    }

    private VoiceSessionEntryPoint requireEntryPoint(VoiceSessionCreateRequest request) {
        if (request.getEntryPoint() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return request.getEntryPoint();
    }

    private String requireTransferAccount(String userId, VoiceSessionEntryPoint entryPoint) {
        if (entryPoint != VoiceSessionEntryPoint.TRANSFER) {
            return null;
        }
        List<BankAccount> activeAccounts = accountMapper.findActiveByUserId(userId);
        if (activeAccounts == null || activeAccounts.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (activeAccounts.size() != 1 || activeAccounts.get(0).getAccountId() == null) {
            throw new BusinessException(ErrorCode.TRANSFER_INVALID_STATE);
        }
        return activeAccounts.get(0).getAccountId();
    }

    /**
     * Loads a user's voice session and expires it when its idle deadline has passed.
     *
     * @return the owned voice session, including any expiration updates
     * @throws BusinessException if the session does not exist for the user
     */
    private VoiceSessionVo findOwnedAndExpireIfNeeded(String userId, String sessionId) {
        VoiceSessionVo voiceSession = voiceSessionMapper.findOwnedById(
                userId, sessionId);
        if (voiceSession == null) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }

        VoiceSessionStatus status = VoiceSessionStatus.valueOf(voiceSession.getStatus());
        if (status != VoiceSessionStatus.CLOSED
                && status != VoiceSessionStatus.EXPIRED
                && !voiceSession.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            cancelUnexecutedTransfer(voiceSession);
            voiceSessionMapper.updateStatusAndStep(
                    userId, sessionId, VoiceSessionStatus.EXPIRED.name(),
                    voiceSession.getCurrentStep());
            voiceInteractionCardMapper.deactivateActiveBySessionId(sessionId);
            voiceGuidanceSettingsService.completeSession(
                    userId, voiceAdaptationSessionStateStore.stateOf(sessionId));
            voiceAdaptationSessionStateStore.clear(sessionId);
            voiceSession = voiceSessionMapper.findOwnedById(userId, sessionId);
        }
        return voiceSession;
    }

    private void cancelUnexecutedTransfer(VoiceSessionVo voiceSession) {
        if (voiceSession.getTransferId() == null) {
            return;
        }
        voiceTransferOrchestrator.cancelUnexecuted(
                UUID.fromString(voiceSession.getUserId()), UUID.fromString(voiceSession.getTransferId()));
    }

    /**
     * Persists the initial AI prompt for a voice session with rendered speech markup.
     *
     * @param userId     the user who owns the session
     * @param sessionId  the voice session identifier
     * @param firstPrompt the initial prompt text
     */
    private void saveFirstPrompt(String userId, String sessionId, String firstPrompt) {
        DialogueTurnVo dialogueTurn = new DialogueTurnVo();
        dialogueTurn.setTurnId(UUID.randomUUID().toString());
        dialogueTurn.setSessionId(sessionId);
        dialogueTurn.setSequenceNo(dialogueTurnMapper.findNextSequenceNo(sessionId));
        dialogueTurn.setSpeaker(AI_SPEAKER);
        dialogueTurn.setTtsText(firstPrompt);
        var state = voiceAdaptationSessionStateStore.stateOf(sessionId);
        var mode = state == null ? com.silvertown.domain.voice.enums.VoiceGuidanceMode.STANDARD : state.mode();
        dialogueTurn.setTtsSsml(mode == com.silvertown.domain.voice.enums.VoiceGuidanceMode.STANDARD
                ? voiceSsmlRenderer.render(userId, firstPrompt)
                : voiceSsmlRenderer.render(userId, firstPrompt, mode));
        dialogueTurn.setStep(DialogueStep.AWAITING_INPUT.name());
        dialogueTurn.setReplayCount(0);
        dialogueTurn.setInterrupted(false);
        dialogueTurnMapper.insert(dialogueTurn);
    }

    private VoiceSessionDetailResponse toDetailResponse(VoiceSessionVo voiceSession) {
        return new VoiceSessionDetailResponse(
                voiceSession.getSessionId(),
                VoiceSessionEntryPoint.valueOf(voiceSession.getEntryPoint()),
                VoiceSessionStatus.valueOf(voiceSession.getStatus()),
                DialogueStep.valueOf(voiceSession.getCurrentStep()),
                VoiceFlowType.valueOf(voiceSession.getFlowType()),
                SttMode.valueOf(voiceSession.getSttMode()),
                toOffsetDateTime(voiceSession.getExpiresAt()),
                voiceSession.getEndedAt() == null ? null : toOffsetDateTime(voiceSession.getEndedAt()),
                latestReplayPayload(voiceSession.getSessionId()),
                navigationFor(
                        VoiceSessionEntryPoint.valueOf(voiceSession.getEntryPoint()),
                        voiceSession.getSessionId()));
    }

    private VoiceSessionNavigationResponse navigationFor(
            VoiceSessionEntryPoint entryPoint, String sessionId) {
        if (entryPoint != VoiceSessionEntryPoint.BILL_PAYMENT) {
            return null;
        }
        return new VoiceSessionNavigationResponse(
                VoiceSessionNavigationScreenCode.BILL_CAMERA, sessionId);
    }

    private VoiceReplayPayloadResponse latestReplayPayload(String sessionId) {
        DialogueTurnVo dialogueTurn = dialogueTurnMapper.findLatestReplayableAiTurn(sessionId);
        if (dialogueTurn == null) {
            return null;
        }
        return new VoiceReplayPayloadResponse(
                dialogueTurn.getTtsText(), dialogueTurn.getTtsSsml(), parseDisplayCard(dialogueTurn));
    }

    private JsonNode parseDisplayCard(DialogueTurnVo dialogueTurn) {
        if (dialogueTurn.getDisplayCard() == null || dialogueTurn.getDisplayCard().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(dialogueTurn.getDisplayCard());
        } catch (JsonProcessingException exception) {
            log.warn("Invalid display card JSON. sessionId={}", dialogueTurn.getSessionId(), exception);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }
}
