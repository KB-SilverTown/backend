package com.silvertown.domain.voice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.transfer.dto.AmountValidationRequest;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.voice.amount.AmountCandidateDecision;
import com.silvertown.domain.voice.amount.AmountCandidateDecisionType;
import com.silvertown.domain.voice.amount.KoreanAmountCandidateGenerator;
import com.silvertown.domain.voice.adaptation.VoiceAdaptationSessionStateStore;
import com.silvertown.domain.voice.adaptation.VoiceGuidanceCommand;
import com.silvertown.domain.voice.adaptation.VoiceGuidanceCommandParser;
import com.silvertown.domain.voice.adaptation.VoiceAdaptationPolicy;
import com.silvertown.domain.voice.dto.VoiceTurnRequest;
import com.silvertown.domain.voice.dto.VoiceTurnResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceAdaptationSignal;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.AccountVoiceResponseResolver;
import com.silvertown.domain.voice.service.BillVoiceResponseResolver;
import com.silvertown.domain.voice.service.MobileBranchVoiceResponseResolver;
import com.silvertown.domain.voice.service.VoiceInteractionCardIssuer;
import com.silvertown.domain.voice.service.VoiceProgressPromptFactory;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.service.VoiceTransferPreparation;
import com.silvertown.domain.voice.service.VoiceTransferRiskCheck;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisCommand;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisPort;
import com.silvertown.domain.voice.service.VoiceTurnAnalysisResult;
import com.silvertown.domain.voice.service.VoiceTurnService;
import com.silvertown.domain.voice.stt.AzureSpeechDetailedResult;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class VoiceTurnServiceImpl implements VoiceTurnService {
    private static final String USER_SPEAKER = "USER";
    private static final String AI_SPEAKER = "AI";
    private static final String SLOTS_FIELD = "slots";
    private static final String CONFIDENCE_FIELD = "confidence";
    private static final String NEXT_ACTION_FIELD = "nextAction";
    private static final String REQUESTED_FUNCTION_FIELD = "requestedFunction";
    private static final String REQUIRED_SLOT_FIELD = "requiredSlot";
    private static final String DRAFT_SUMMARY_FIELD = "draftSummary";
    private static final String VOICE_CARD_ACTION_FIELD = "_voiceCardAction";

    private final VoiceSessionMapper voiceSessionMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final VoiceTurnAnalysisPort voiceTurnAnalysisPort;
    private final VoiceProgressPromptFactory voiceProgressPromptFactory;
    private final VoiceSsmlRenderer voiceSsmlRenderer;
    private final KoreanAmountCandidateGenerator amountCandidateGenerator;
    private final VoiceTransferOrchestrator voiceTransferOrchestrator;
    private final VoiceInteractionCardIssuer voiceInteractionCardIssuer;
    private final VoiceInteractionCardMapper voiceInteractionCardMapper;
    private final AccountVoiceResponseResolver accountVoiceResponseResolver;
    private final BillVoiceResponseResolver billVoiceResponseResolver;
    private final MobileBranchVoiceResponseResolver mobileBranchVoiceResponseResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final VoiceGuidanceCommandParser voiceGuidanceCommandParser;
    private final VoiceAdaptationSessionStateStore voiceAdaptationSessionStateStore;

    /** Compatibility constructor retained for focused unit and integration tests. */
    public VoiceTurnServiceImpl(
            VoiceSessionMapper voiceSessionMapper,
            DialogueTurnMapper dialogueTurnMapper,
            VoiceTurnAnalysisPort voiceTurnAnalysisPort,
            VoiceProgressPromptFactory voiceProgressPromptFactory,
            VoiceSsmlRenderer voiceSsmlRenderer,
            KoreanAmountCandidateGenerator amountCandidateGenerator,
            VoiceTransferOrchestrator voiceTransferOrchestrator,
            VoiceInteractionCardIssuer voiceInteractionCardIssuer,
            VoiceInteractionCardMapper voiceInteractionCardMapper,
            AccountVoiceResponseResolver accountVoiceResponseResolver,
            BillVoiceResponseResolver billVoiceResponseResolver,
            MobileBranchVoiceResponseResolver mobileBranchVoiceResponseResolver,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this(voiceSessionMapper, dialogueTurnMapper, voiceTurnAnalysisPort, voiceProgressPromptFactory,
                voiceSsmlRenderer, amountCandidateGenerator, voiceTransferOrchestrator,
                voiceInteractionCardIssuer, voiceInteractionCardMapper, accountVoiceResponseResolver,
                billVoiceResponseResolver, mobileBranchVoiceResponseResolver, objectMapper, clock,
                transactionManager, new VoiceGuidanceCommandParser(),
                new VoiceAdaptationSessionStateStore(new VoiceAdaptationPolicy()));
    }

    @Override
    public VoiceTurnResponse process(String userId, String sessionId, VoiceTurnRequest request) {
        return processInternal(userId, sessionId, request,
                voiceSession -> analyzeRawOrContinuationTurn(request, voiceSession));
    }

    @Override
    public VoiceTurnResponse processAzureTransferFinal(
            String userId, String sessionId, String turnId, AzureSpeechDetailedResult result) {
        if (result == null || isBlank(result.transcript())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        BigDecimal confidence = result.confidence() == null ? BigDecimal.ZERO : result.confidence();
        VoiceTurnRequest request = VoiceTurnRequest.azureFinal(turnId, result.transcript(), confidence);
        return processInternal(userId, sessionId, request, voiceSession -> {
            VoiceTurnAnalysisResult contextual = resolveVoiceCardContext(request, voiceSession);
            return contextual != null ? contextual : analyzeAzureTransferFinal(request, result, voiceSession);
        });
    }

    private VoiceTurnResponse processInternal(
            String userId,
            String sessionId,
            VoiceTurnRequest request,
            java.util.function.Function<VoiceSessionVo, VoiceTurnAnalysisResult> analysisResolver) {
        TurnClaim claim = claimTurn(userId, sessionId, request);
        if (claim.existingUserTurn() != null) {
            return reuseOrRejectExistingTurn(claim.existingUserTurn(), request);
        }

        try {
            VoiceGuidanceCommand command = voiceGuidanceCommandParser.parse(request.getTranscript()).orElse(null);
            VoiceTurnAnalysisResult resolved = command == null
                    ? analysisResolver.apply(claim.voiceSession())
                    : adaptationCommandResponse(request, claim.voiceSession());
            VoiceTurnAnalysisResult analysis = enrichRecipientCandidates(claim.voiceSession(), resolved);
            VoiceTurnResponse response = persistAndCompleteTurn(
                    userId, sessionId, request, claim.voiceSession(), analysis);
            completeAdaptationSignalHandling(claim.voiceSession(), request, analysis, command, response);
            return response;
        } catch (DuplicateKeyException exception) {
            restoreTurnClaim(userId, sessionId, claim.previousStatus(), exception);
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        } catch (RuntimeException exception) {
            restoreTurnClaim(userId, sessionId, claim.previousStatus(), exception);
            throw exception;
        }
    }

    private VoiceTurnAnalysisResult adaptationCommandResponse(
            VoiceTurnRequest request, VoiceSessionVo voiceSession) {
        VoiceTurnAnalysisResult contextual = resolveVoiceCardContext(request, voiceSession);
        if (contextual != null) {
            return contextual;
        }
        DialogueTurnVo source = dialogueTurnMapper.findLatestBusinessAiTurn(voiceSession.getSessionId());
        if (source == null) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        return voiceProgressPromptFactory.resumePrompt(source);
    }

    private void completeAdaptationSignalHandling(
            VoiceSessionVo voiceSession,
            VoiceTurnRequest request,
            VoiceTurnAnalysisResult analysis,
            VoiceGuidanceCommand command,
            VoiceTurnResponse response) {
        DialogueStep currentStep = DialogueStep.valueOf(voiceSession.getCurrentStep());
        EnumSet<VoiceAdaptationSignal> signals = EnumSet.noneOf(VoiceAdaptationSignal.class);
        if (command != null && command.adaptationSignal() != null) {
            signals.add(command.adaptationSignal());
        }
        if (request.getInputType().name().equals("VOICE")
                && request.getSttConfidence() != null
                && request.getSttConfidence().compareTo(new BigDecimal("0.70")) < 0) {
            signals.add(VoiceAdaptationSignal.LOW_STT_CONFIDENCE);
        }
        if (analysis.getNextAction() == VoiceNextAction.RECONFIRM_INPUT) {
            signals.add(VoiceAdaptationSignal.FINANCIAL_RECONFIRMATION);
        }
        voiceAdaptationSessionStateStore.recordSignals(voiceSession.getSessionId(), currentStep, signals);
        if (analysis.getNextAction() == VoiceNextAction.REASK_INPUT
                || analysis.getNextAction() == VoiceNextAction.RECONFIRM_INPUT) {
            voiceAdaptationSessionStateStore.recordReask(voiceSession.getSessionId(), currentStep);
        } else if (command == null && analysis.getNextStep() != currentStep) {
            voiceAdaptationSessionStateStore.recordNormalAdvance(voiceSession.getSessionId(), currentStep);
        }
        if (response.getState() == DialogueStep.CANCELLED) {
            voiceAdaptationSessionStateStore.clear(voiceSession.getSessionId());
            return;
        }
        voiceAdaptationSessionStateStore.responseRendered(voiceSession.getSessionId(), currentStep);
    }

    private VoiceTurnAnalysisResult analyzeRawTurn(VoiceTurnRequest request, VoiceSessionVo voiceSession) {
        rejectRawBackendTransferTurn(voiceSession);
        VoiceTurnAnalysisResult analysis = analyze(request, voiceSession);
        if (VoiceFlowType.valueOf(voiceSession.getFlowType()) != VoiceFlowType.GENERAL_FINANCE) {
            return analysis;
        }
        VoiceTurnAnalysisResult billResolved = billVoiceResponseResolver.resolve(
                voiceSession.getUserId(), voiceSession.getSessionId(), request.getTranscript(), analysis);
        VoiceTurnAnalysisResult accountResolved = accountVoiceResponseResolver.resolve(
                voiceSession.getUserId(), billResolved);
        return mobileBranchVoiceResponseResolver.resolve(accountResolved);
    }

    private VoiceTurnAnalysisResult analyzeRawOrContinuationTurn(
            VoiceTurnRequest request, VoiceSessionVo voiceSession) {
        VoiceTurnAnalysisResult contextual = resolveVoiceCardContext(request, voiceSession);
        if (contextual != null) {
            return contextual;
        }
        if (DialogueStep.valueOf(voiceSession.getCurrentStep()) == DialogueStep.AWAITING_CONTINUATION) {
            return analyzeContinuationResponse(request, voiceSession);
        }
        return analyzeRawTurn(request, voiceSession);
    }

    private VoiceTurnAnalysisResult analyzeContinuationResponse(
            VoiceTurnRequest request, VoiceSessionVo voiceSession) {
        DialogueTurnVo continuationPrompt = dialogueTurnMapper.findLatestBySessionId(
                voiceSession.getSessionId());
        if (continuationPrompt == null
                || !AI_SPEAKER.equals(continuationPrompt.getSpeaker())
                || continuationPrompt.isInterrupted()
                || !DialogueStep.AWAITING_CONTINUATION.name().equals(continuationPrompt.getStep())) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }

        if (isContinuationAccepted(request.getTranscript())) {
            DialogueTurnVo sourcePrompt = dialogueTurnMapper.findLatestBusinessAiTurn(
                    voiceSession.getSessionId());
            if (sourcePrompt == null) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            return voiceProgressPromptFactory.resumePrompt(sourcePrompt);
        }
        if (isContinuationDeclined(request.getTranscript())) {
            return voiceProgressPromptFactory.closePrompt();
        }
        return voiceProgressPromptFactory.clarifyContinuation();
    }

    private boolean isContinuationAccepted(String transcript) {
        String normalized = normalizeContinuationResponse(transcript);
        return normalized.equals("네")
                || normalized.equals("예")
                || normalized.equals("응")
                || normalized.equals("그래")
                || normalized.equals("좋아요")
                || normalized.equals("계속")
                || normalized.equals("계속할게요");
    }

    private boolean isContinuationDeclined(String transcript) {
        String normalized = normalizeContinuationResponse(transcript);
        return normalized.equals("아니요")
                || normalized.equals("아니")
                || normalized.equals("그만")
                || normalized.equals("끝낼게요")
                || normalized.equals("종료");
    }

    private String normalizeContinuationResponse(String transcript) {
        return transcript == null ? "" : transcript.replaceAll("[\\s.,!?]", "").trim();
    }

    private VoiceTurnAnalysisResult analyze(VoiceTurnRequest request, VoiceSessionVo voiceSession) {
        return voiceTurnAnalysisPort.analyze(new VoiceTurnAnalysisCommand(
                voiceSession.getUserId(),
                voiceSession.getSessionId(),
                request.getTurnId(),
                VoiceFlowType.valueOf(voiceSession.getFlowType()),
                DialogueStep.valueOf(voiceSession.getCurrentStep()),
                request.getTranscript(),
                request.getSttConfidence(),
                request.getInputType()));
    }

    private VoiceTurnAnalysisResult enrichRecipientCandidates(
            VoiceSessionVo voiceSession, VoiceTurnAnalysisResult analysis) {
        if (VoiceFlowType.valueOf(voiceSession.getFlowType()) != VoiceFlowType.TRANSFER
                || analysis.getIntent() != VoiceIntent.TRANSFER) {
            return analysis;
        }
        DialogueStep currentStep = DialogueStep.valueOf(voiceSession.getCurrentStep());
        if (currentStep != DialogueStep.AWAITING_INPUT
                && currentStep != DialogueStep.AWAITING_RECIPIENT) {
            return analysis;
        }
        Object recipient = analysis.getSlots().get("recipient");
        if (recipient == null) {
            recipient = analysis.getSlots().get("recipientName");
        }
        if (recipient == null || String.valueOf(recipient).isBlank()) {
            return analysis;
        }
        java.util.List<RecipientCandidateResponse> candidates = voiceTransferOrchestrator
                .findRecipientCandidates(UUID.fromString(voiceSession.getUserId()), String.valueOf(recipient).trim());
        if (candidates.isEmpty()) {
            return analysis;
        }
        java.util.List<RecipientCandidateResponse> visibleCandidates = candidates.stream().limit(2).toList();
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", "RECIPIENT_CANDIDATES");
        java.util.List<Long> amountCandidates = amountCandidates(analysis.getSlots());
        if (!amountCandidates.isEmpty()) {
            displayCard.set("pendingAmountCandidates", objectMapper.valueToTree(amountCandidates));
        }
        com.fasterxml.jackson.databind.node.ArrayNode items = displayCard.putArray("items");
        for (RecipientCandidateResponse candidate : visibleCandidates) {
            ObjectNode item = items.addObject();
            item.put("recipientId", candidate.getRecipientId().toString());
            item.put("displayName", candidate.getDisplayName());
            item.put("relationship", candidate.getRelationship());
            item.put("bankCode", candidate.getBankCode());
            item.put("accountNumberMasked", candidate.getAccountNumberMasked());
            item.put("source", candidate.getSource());
            if (candidate.getLastUsedAt() != null) {
                item.put("lastUsedAt", candidate.getLastUsedAt().toString());
            }
        }
        String ttsText = visibleCandidates.get(0).getDisplayName()
                + "님에게 돈을 보내시려는 게 맞을까요?";
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_RECIPIENT,
                VoiceIntent.TRANSFER,
                analysis.getSlots(),
                analysis.getConfidence(),
                ttsText,
                analysis.getTtsSsml(),
                displayCard,
                objectMapper.createObjectNode().put("name", "recipientConfirmation"),
                analysis.getDraftSummary(),
                VoiceNextAction.ASK_RECIPIENT,
                VoiceRequestedFunction.NONE);
    }

    private VoiceTurnAnalysisResult resolveVoiceCardContext(
            VoiceTurnRequest request, VoiceSessionVo session) {
        VoiceInteractionCardVo card = voiceInteractionCardMapper.findBySessionId(session.getSessionId());
        if (card == null || !card.isActive()) {
            return null;
        }
        if ("TRANSFER_RISK_CHECK".equals(card.getCardType())
                && DialogueStep.valueOf(session.getCurrentStep()) == DialogueStep.RISK_CHECK) {
            return resolveRiskCheck(session, card, request.getTranscript());
        }
        if ("TRANSFER_HELD".equals(card.getCardType())
                && DialogueStep.valueOf(session.getCurrentStep()) == DialogueStep.HELD) {
            return contextualResult(
                    DialogueStep.HELD,
                    "보호자 확인이 끝날 때까지 잠시 기다려 주세요.",
                    null,
                    VoiceNextAction.WAIT_GUARDIAN_VERIFICATION,
                    null);
        }
        if ("TRANSFER_READBACK".equals(card.getCardType())
                && DialogueStep.valueOf(session.getCurrentStep()) == DialogueStep.WAITING_FINAL_APPROVAL
                && session.getTransferId() != null) {
            UUID userId = UUID.fromString(session.getUserId());
            UUID transferId = UUID.fromString(session.getTransferId());
            if (isFinalApproval(request.getTranscript())) {
                return contextualResult(
                        DialogueStep.WAITING_FINAL_APPROVAL,
                        "간편 비밀번호를 확인해 주세요.",
                        null,
                        VoiceNextAction.ASK_PIN,
                        "FINAL_ACCEPT");
            }
            if (isFinalRejection(request.getTranscript())) {
                return contextualResult(
                        DialogueStep.CANCELLED,
                        "돈 보내기를 취소했어요.",
                        null,
                        VoiceNextAction.END_SESSION,
                        "FINAL_REJECT");
            }
            TransferResponse readback = voiceTransferOrchestrator.getTransfer(userId, transferId);
            return contextualResult(
                    DialogueStep.WAITING_FINAL_APPROVAL,
                    readback.getConfirmationText() + " 맞으면 네, 아니면 아니요라고 말씀해 주세요.",
                    transferCard("TRANSFER_READBACK", readback),
                    VoiceNextAction.ASK_FINAL_APPROVAL,
                    null);
        }
        if ("RECIPIENT_CANDIDATES".equals(card.getCardType())
                && DialogueStep.valueOf(session.getCurrentStep()) == DialogueStep.AWAITING_RECIPIENT) {
            VoiceTurnAnalysisResult ordinalSelection = focusRecipientByOrdinal(card, request.getTranscript());
            if (ordinalSelection != null) {
                return ordinalSelection;
            }
        }
        if (!isPositiveAcceptance(request.getTranscript())) {
            return null;
        }
        if (("RECIPIENT_CANDIDATES".equals(card.getCardType())
                || "AMOUNT_RECONFIRM".equals(card.getCardType()))
                && card.getFocusedItemId() == null) {
            return reaskFocusedSelection(card);
        }
        if ("RECIPIENT_CANDIDATES".equals(card.getCardType()) && card.getFocusedItemId() != null) {
            java.util.List<Long> pendingAmountCandidates = pendingAmountCandidates(card);
            if (!pendingAmountCandidates.isEmpty()) {
                return withVoiceCardAction(
                        safeAmountReconfirm(BigDecimal.ONE, pendingAmountCandidates), "RECIPIENT_ACCEPT");
            }
            return contextualResult(
                    DialogueStep.AWAITING_AMOUNT,
                    "보낼 금액을 말씀해 주세요.",
                    null,
                    VoiceNextAction.ASK_AMOUNT,
                    "RECIPIENT_ACCEPT");
        }
        if ("AMOUNT_RECONFIRM".equals(card.getCardType()) && card.getFocusedItemId() != null) {
            return resolveAmountAcceptance(session, card);
        }
        return null;
    }

    private VoiceTurnAnalysisResult reaskFocusedSelection(VoiceInteractionCardVo card) {
        boolean recipient = "RECIPIENT_CANDIDATES".equals(card.getCardType());
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", card.getCardType());
        displayCard.putNull("focusedItemId");
        try {
            JsonNode items = objectMapper.readTree(card.getCandidateItems());
            if (recipient) {
                displayCard.set("items", items);
            } else {
                com.fasterxml.jackson.databind.node.ArrayNode amounts = displayCard.putArray("amountCandidates");
                for (JsonNode item : items) {
                    if (item.path("amount").canConvertToLong()) {
                        amounts.add(item.path("amount").longValue());
                    }
                }
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return contextualResult(
                recipient ? DialogueStep.AWAITING_RECIPIENT : DialogueStep.RECONFIRMING,
                "화면에서 후보를 선택한 뒤 다시 확인해 주세요.",
                displayCard,
                VoiceNextAction.RECONFIRM_INPUT,
                null);
    }

    private VoiceTurnAnalysisResult focusRecipientByOrdinal(VoiceInteractionCardVo card, String transcript) {
        int candidateIndex = recipientCandidateIndex(transcript);
        if (candidateIndex < 0) {
            return null;
        }
        try {
            JsonNode items = objectMapper.readTree(card.getCandidateItems());
            if (!items.isArray() || candidateIndex >= items.size()) {
                return reaskFocusedSelection(card);
            }
            JsonNode selected = items.get(candidateIndex);
            String recipientId = selected.path("id").asText();
            String displayName = selected.path("displayName").asText();
            if (recipientId.isBlank() || displayName.isBlank()) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            ObjectNode displayCard = objectMapper.createObjectNode();
            displayCard.put("type", "RECIPIENT_CANDIDATES");
            displayCard.set("items", items);
            displayCard.put("focusedItemId", recipientId);
            return contextualResult(
                    DialogueStep.AWAITING_RECIPIENT,
                    displayName + "님에게 돈을 보내시려는 게 맞을까요?",
                    displayCard,
                    VoiceNextAction.RECONFIRM_INPUT,
                    null);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private int recipientCandidateIndex(String transcript) {
        String normalized = normalizeContinuationResponse(transcript);
        if (normalized.equals("첫번째") || normalized.equals("1번") || normalized.equals("일번")) {
            return 0;
        }
        if (normalized.equals("두번째") || normalized.equals("2번")) {
            return 1;
        }
        return -1;
    }

    private VoiceTurnAnalysisResult resolveAmountAcceptance(
            VoiceSessionVo session, VoiceInteractionCardVo card) {
        long amount = focusedAmount(card);
        if (session.getFromAccountId() == null || card.getConfirmedRecipientId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        VoiceTransferPreparation preparation = voiceTransferOrchestrator.prepareAndAssess(
                UUID.fromString(session.getUserId()),
                UUID.fromString(session.getSessionId()),
                UUID.fromString(session.getFromAccountId()),
                UUID.fromString(card.getConfirmedRecipientId()),
                amount);
        AmountValidationResponse validation = preparation.amountValidation();
        if (validation.isAmountReconfirmRequired()
                || validation.getConfirmedAmount() == null
                || validation.getConfirmedAmount() != amount) {
            return safeAmountReconfirm(BigDecimal.ONE, validation.getAmountCandidates());
        }
        var risk = preparation.risk();
        TransferResponse readback = preparation.readback();
        if (!risk.isAdditionalCheckRequired()) {
            return contextualResult(
                    DialogueStep.WAITING_FINAL_APPROVAL,
                    readback.getConfirmationText(),
                    transferCard("TRANSFER_READBACK", readback),
                    VoiceNextAction.ASK_FINAL_APPROVAL,
                    "AMOUNT_ACCEPT:" + validation.getConfirmedAmount());
        }
        boolean held = "HOLD".equals(risk.getRecommendedAction())
                || "HIGH".equals(risk.getLevel()) || "CRITICAL".equals(risk.getLevel());
        return contextualResult(
                held ? DialogueStep.HELD : DialogueStep.RISK_CHECK,
                held ? "안전을 위해 송금을 잠시 확인할게요." : "안전을 위해 돈을 보내는 이유를 말씀해 주세요.",
                transferCard(held ? "TRANSFER_HELD" : "TRANSFER_RISK_CHECK", readback),
                held ? VoiceNextAction.WAIT_GUARDIAN_VERIFICATION : VoiceNextAction.NONE,
                "AMOUNT_ACCEPT:" + validation.getConfirmedAmount());
    }

    private VoiceTurnAnalysisResult resolveRiskCheck(
            VoiceSessionVo session, VoiceInteractionCardVo card, String purposeAnswer) {
        if (session.getTransferId() == null || isBlank(purposeAnswer)) {
            return null;
        }
        VoiceTransferRiskCheck checkedTransfer = voiceTransferOrchestrator.checkRisk(
                UUID.fromString(session.getUserId()), UUID.fromString(session.getTransferId()), purposeAnswer);
        var checked = checkedTransfer.risk();
        TransferResponse transfer = checkedTransfer.transfer();
        if (!checked.isAdditionalCheckRequired()) {
            return contextualResult(
                    DialogueStep.WAITING_FINAL_APPROVAL,
                    transfer.getConfirmationText(),
                    transferCard("TRANSFER_READBACK", transfer),
                    VoiceNextAction.ASK_FINAL_APPROVAL,
                    null);
        }
        boolean held = checked.isHold() || checked.isVerificationRequired();
        return contextualResult(
                held ? DialogueStep.HELD : DialogueStep.RISK_CHECK,
                checked.getWarningTtsText(),
                transferCard(held ? "TRANSFER_HELD" : "TRANSFER_RISK_CHECK", transfer),
                held ? VoiceNextAction.WAIT_GUARDIAN_VERIFICATION : VoiceNextAction.NONE,
                null);
    }

    private VoiceTurnAnalysisResult contextualResult(
            DialogueStep step,
            String ttsText,
            JsonNode displayCard,
            VoiceNextAction nextAction,
            String action) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (action != null) {
            slots.put(VOICE_CARD_ACTION_FIELD, action);
        }
        return new VoiceTurnAnalysisResult(
                step,
                VoiceIntent.TRANSFER,
                slots,
                BigDecimal.ONE,
                ttsText,
                null,
                displayCard,
                null,
                null,
                nextAction,
                VoiceRequestedFunction.NONE);
    }

    private JsonNode transferCard(String type, TransferResponse transfer) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", type);
        card.put("transferId", transfer.getTransferId().toString());
        card.put("recipientName", transfer.getRecipient().getDisplayName());
        card.put("amount", transfer.getAmount());
        return card;
    }

    private long focusedAmount(VoiceInteractionCardVo card) {
        try {
            JsonNode candidates = objectMapper.readTree(card.getCandidateItems());
            for (JsonNode candidate : candidates) {
                if (card.getFocusedItemId().equals(candidate.path("id").asText())
                        && candidate.path("amount").canConvertToLong()) {
                    return candidate.path("amount").longValue();
                }
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private java.util.List<Long> amountCandidates(Map<String, Object> slots) {
        Object value = slots.get("amountCandidates");
        if (!(value instanceof java.util.Collection<?>)) {
            return java.util.List.of();
        }
        java.util.List<Long> candidates = new java.util.ArrayList<>();
        for (Object candidate : (java.util.Collection<?>) value) {
            if (candidate instanceof Number && ((Number) candidate).longValue() > 0) {
                candidates.add(((Number) candidate).longValue());
            }
        }
        return candidates;
    }

    private java.util.List<Long> pendingAmountCandidates(VoiceInteractionCardVo card) {
        try {
            JsonNode items = objectMapper.readTree(card.getCandidateItems());
            if (!items.isArray() || items.isEmpty()) {
                return java.util.List.of();
            }
            JsonNode candidates = items.get(0).path("pendingAmountCandidates");
            if (!candidates.isArray()) {
                return java.util.List.of();
            }
            java.util.List<Long> result = new java.util.ArrayList<>();
            for (JsonNode candidate : candidates) {
                if (!candidate.canConvertToLong() || candidate.longValue() <= 0) {
                    return java.util.List.of();
                }
                result.add(candidate.longValue());
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private VoiceTurnAnalysisResult withVoiceCardAction(
            VoiceTurnAnalysisResult analysis, String action) {
        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.put(VOICE_CARD_ACTION_FIELD, action);
        return new VoiceTurnAnalysisResult(
                analysis.getNextStep(), analysis.getIntent(), slots, analysis.getConfidence(),
                analysis.getTtsText(), analysis.getTtsSsml(), analysis.getDisplayCard(),
                analysis.getRequiredSlot(), analysis.getDraftSummary(), analysis.getNextAction(),
                analysis.getRequestedFunction());
    }

    private boolean isPositiveAcceptance(String transcript) {
        String normalized = normalizeContinuationResponse(transcript);
        return normalized.equals("네") || normalized.equals("예") || normalized.equals("응")
                || normalized.equals("맞아") || normalized.equals("좋아요")
                || normalized.equals("이걸로할래") || normalized.equals("이걸로해줘")
                || normalized.equals("그사람에게해줘");
    }

    private boolean isFinalApproval(String transcript) {
        String normalized = normalizeContinuationResponse(transcript);
        return normalized.equals("네") || normalized.equals("예")
                || normalized.equals("승인") || normalized.equals("보내");
    }

    private boolean isFinalRejection(String transcript) {
        String normalized = normalizeContinuationResponse(transcript);
        return normalized.equals("아니요") || normalized.equals("아니") || normalized.equals("아니야")
                || normalized.equals("취소") || normalized.equals("취소해줘") || normalized.equals("그만");
    }

    /** TRANSFER + BACKEND_STREAM may enter only through the Azure FINAL boundary. */
    private void rejectRawBackendTransferTurn(VoiceSessionVo voiceSession) {
        if (VoiceFlowType.valueOf(voiceSession.getFlowType()) == VoiceFlowType.TRANSFER
                && "BACKEND_STREAM".equals(voiceSession.getSttMode())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private VoiceTurnAnalysisResult analyzeAzureTransferFinal(
            VoiceTurnRequest request,
            AzureSpeechDetailedResult result,
            VoiceSessionVo voiceSession) {
        if (VoiceFlowType.valueOf(voiceSession.getFlowType()) != VoiceFlowType.TRANSFER
                || !"BACKEND_STREAM".equals(voiceSession.getSttMode())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        AmountCandidateDecision decision = amountCandidateGenerator.decide(result);
        logAmountDecision(decision, request.getSttConfidence());
        if (decision.type() == AmountCandidateDecisionType.REASK) {
            return safeAmountReask(request.getSttConfidence());
        }

        AmountValidationResponse validation = voiceTransferOrchestrator.validateAmount(
                amountValidationRequest(decision));
        if (decision.type() == AmountCandidateDecisionType.RECONFIRM) {
            if (!validation.isAmountReconfirmRequired() || validation.getConfirmedAmount() != null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            return safeAmountReconfirm(request.getSttConfidence(), validation.getAmountCandidates());
        }
        if (validation.isAmountReconfirmRequired()
                || validation.getConfirmedAmount() == null
                || !validation.getConfirmedAmount().equals(decision.recognizedAmount())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return retainOnlyAzureAmount(analyze(request, voiceSession), validation.getConfirmedAmount());
    }

    private AmountValidationRequest amountValidationRequest(AmountCandidateDecision decision) {
        return AmountValidationRequest.of(decision.recognizedAmount(), decision.candidates());
    }

    private VoiceTurnAnalysisResult retainOnlyAzureAmount(VoiceTurnAnalysisResult analysis, Long amount) {
        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.remove("amount");
        slots.remove("amountCandidates");
        slots.put("amount", amount);
        slots.put("amountCandidates", java.util.List.of(amount));
        return new VoiceTurnAnalysisResult(
                analysis.getNextStep(),
                analysis.getIntent(),
                slots,
                analysis.getConfidence(),
                analysis.getTtsText(),
                analysis.getTtsSsml(),
                trustedDisplayCard(analysis.getDisplayCard(), amount),
                analysis.getRequiredSlot(),
                objectMapper.valueToTree(slots),
                analysis.getNextAction(),
                analysis.getRequestedFunction());
    }

    private JsonNode trustedDisplayCard(JsonNode displayCard, Long amount) {
        ObjectNode trustedCard = displayCard != null && displayCard.isObject()
                ? (ObjectNode) displayCard.deepCopy()
                : objectMapper.createObjectNode();
        trustedCard.put("amount", amount);
        trustedCard.set("amountCandidates", objectMapper.valueToTree(java.util.List.of(amount)));
        return trustedCard;
    }

    private VoiceTurnAnalysisResult safeAmountReask(BigDecimal confidence) {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.TRANSFER,
                Map.of(),
                confidence,
                "보낼 금액을 원 단위로 다시 말씀해 주세요.",
                "<speak>보낼 금액을 원 단위로 다시 말씀해 주세요.</speak>",
                null,
                null,
                null,
                VoiceNextAction.REASK_INPUT,
                VoiceRequestedFunction.NONE);
    }

    private VoiceTurnAnalysisResult safeAmountReconfirm(BigDecimal confidence, java.util.List<Long> candidates) {
        Map<String, Object> slots = Map.of("amountCandidates", candidates);
        String ttsText = String.format("보낼 금액은 %,d원이 맞을까요?", candidates.get(0));
        ObjectNode displayCard = objectMapper.createObjectNode();
        displayCard.put("type", "AMOUNT_RECONFIRM");
        displayCard.set("amountCandidates", objectMapper.valueToTree(candidates));
        return new VoiceTurnAnalysisResult(
                DialogueStep.RECONFIRMING,
                VoiceIntent.TRANSFER,
                slots,
                confidence,
                ttsText,
                "<speak>" + ttsText + "</speak>",
                displayCard,
                null,
                objectMapper.valueToTree(slots),
                VoiceNextAction.RECONFIRM_INPUT,
                VoiceRequestedFunction.TRANSFER_AMOUNT_VALIDATION);
    }

    private void logAmountDecision(AmountCandidateDecision decision, BigDecimal confidence) {
        log.info("Transfer amount candidate decision. candidateCount={}, confidenceBand={}, reconfirmRequired={}",
                decision.candidates().size(), confidenceBand(confidence),
                decision.type() == AmountCandidateDecisionType.RECONFIRM);
    }

    private String confidenceBand(BigDecimal confidence) {
        if (confidence == null || confidence.compareTo(new BigDecimal("0.70")) < 0) {
            return "LOW";
        }
        return confidence.compareTo(new BigDecimal("0.90")) < 0 ? "MEDIUM" : "HIGH";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private TurnClaim claimTurn(String userId, String sessionId, VoiceTurnRequest request) {
        return inTransaction(() -> {
            VoiceSessionVo voiceSession = findOwnedSessionForTurn(userId, sessionId);
            if (voiceSession == null) {
                throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
            }

            DialogueTurnVo existingUserTurn = dialogueTurnMapper.findBySessionIdAndTurnId(
                    sessionId, request.getTurnId());
            expireSessionIfNeeded(userId, sessionId, voiceSession);
            if (existingUserTurn != null) {
                return TurnClaim.existing(voiceSession, existingUserTurn);
            }

            rejectUnavailableSession(voiceSession);
            if (voiceSessionMapper.claimForTurn(userId, sessionId, LocalDateTime.now(clock)) != 1) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            return TurnClaim.claimed(voiceSession);
        });
    }

    private VoiceTurnResponse persistAndCompleteTurn(
            String userId,
            String sessionId,
            VoiceTurnRequest request,
            VoiceSessionVo claimedSession,
            VoiceTurnAnalysisResult analysis) {
        return inTransaction(() -> {
            VoiceSessionVo currentSession = findOwnedSessionForTurn(userId, sessionId);
            if (currentSession == null) {
                throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
            }
            if (VoiceSessionStatus.valueOf(currentSession.getStatus()) != VoiceSessionStatus.PROCESSING) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }

            String voiceCardAction = voiceCardAction(analysis);
            VoiceTurnAnalysisResult renderedAnalysis = withRenderedSsml(userId, withoutVoiceCardAction(analysis));
            int userSequenceNo = dialogueTurnMapper.findNextSequenceNo(sessionId);
            dialogueTurnMapper.insert(userTurn(sessionId, userSequenceNo, request, claimedSession));

            int aiSequenceNo = dialogueTurnMapper.findNextSequenceNo(sessionId);
            DialogueTurnVo aiTurn = aiTurn(sessionId, aiSequenceNo, renderedAnalysis);
            dialogueTurnMapper.insert(aiTurn);
            applyVoiceCardAction(sessionId, aiTurn.getTurnId(), voiceCardAction);
            confirmFinalTransfer(currentSession, voiceCardAction);
            JsonNode issuedCard = voiceInteractionCardIssuer.issueIfInteractive(
                    sessionId, aiTurn.getTurnId(), renderedAnalysis.getDisplayCard());
            if (!Objects.equals(issuedCard, renderedAnalysis.getDisplayCard())) {
                dialogueTurnMapper.updateDisplayCard(aiTurn.getTurnId(), writeJson(issuedCard));
                renderedAnalysis = new VoiceTurnAnalysisResult(
                        renderedAnalysis.getNextStep(),
                        renderedAnalysis.getIntent(),
                        renderedAnalysis.getSlots(),
                        renderedAnalysis.getConfidence(),
                        renderedAnalysis.getTtsText(),
                        renderedAnalysis.getTtsSsml(),
                        issuedCard,
                        renderedAnalysis.getRequiredSlot(),
                        renderedAnalysis.getDraftSummary(),
                        renderedAnalysis.getNextAction(),
                        renderedAnalysis.getRequestedFunction());
            } else if (!keepsActiveHeldCard(currentSession, renderedAnalysis)) {
                voiceInteractionCardMapper.deactivateActiveBySessionId(sessionId);
            }
            if (renderedAnalysis.getNextStep() == DialogueStep.CANCELLED) {
                if (voiceSessionMapper.closeOwned(
                        userId, sessionId, DialogueStep.CANCELLED.name(), LocalDateTime.now(clock)) != 1) {
                    throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
                }
            } else if (voiceSessionMapper.completeTurn(
                    userId, sessionId, renderedAnalysis.getNextStep().name()) != 1) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }

            return toResponse(sessionId, request.getTurnId(), renderedAnalysis);
        });
    }

    /**
     * A guardian-verification hold is owned by the transfer domain.  While it remains held,
     * subsequent voice turns must not clear the active card that keeps the turn processor on
     * the server-side hold path.
     */
    private boolean keepsActiveHeldCard(VoiceSessionVo session, VoiceTurnAnalysisResult analysis) {
        return DialogueStep.HELD.name().equals(session.getCurrentStep())
                && analysis.getNextStep() == DialogueStep.HELD
                && analysis.getNextAction() == VoiceNextAction.WAIT_GUARDIAN_VERIFICATION;
    }

    private VoiceTurnAnalysisResult withRenderedSsml(String userId, VoiceTurnAnalysisResult analysis) {
        return new VoiceTurnAnalysisResult(
                analysis.getNextStep(),
                analysis.getIntent(),
                analysis.getSlots(),
                analysis.getConfidence(),
                analysis.getTtsText(),
                voiceSsmlRenderer.render(userId, analysis.getTtsText()),
                analysis.getDisplayCard(),
                analysis.getRequiredSlot(),
                analysis.getDraftSummary(),
                analysis.getNextAction(),
                analysis.getRequestedFunction());
    }

    private String voiceCardAction(VoiceTurnAnalysisResult analysis) {
        Object action = analysis.getSlots().get(VOICE_CARD_ACTION_FIELD);
        return action == null ? null : String.valueOf(action);
    }

    private VoiceTurnAnalysisResult withoutVoiceCardAction(VoiceTurnAnalysisResult analysis) {
        if (!analysis.getSlots().containsKey(VOICE_CARD_ACTION_FIELD)) {
            return analysis;
        }
        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.remove(VOICE_CARD_ACTION_FIELD);
        return new VoiceTurnAnalysisResult(
                analysis.getNextStep(),
                analysis.getIntent(),
                slots,
                analysis.getConfidence(),
                analysis.getTtsText(),
                analysis.getTtsSsml(),
                analysis.getDisplayCard(),
                analysis.getRequiredSlot(),
                analysis.getDraftSummary(),
                analysis.getNextAction(),
                analysis.getRequestedFunction());
    }

    private void applyVoiceCardAction(String sessionId, String sourceTurnId, String action) {
        if (action == null) {
            return;
        }
        VoiceInteractionCardVo current = voiceInteractionCardMapper.findBySessionIdForUpdate(sessionId);
        if (current == null) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        VoiceInteractionCardVo next = copyCard(current, sourceTurnId);
        if ("RECIPIENT_ACCEPT".equals(action)) {
            if (!"RECIPIENT_CANDIDATES".equals(current.getCardType())
                    || current.getFocusedItemId() == null) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            next.setConfirmedRecipientId(current.getFocusedItemId());
            next.setActive(false);
        } else if (action.startsWith("AMOUNT_ACCEPT:")) {
            if (!"AMOUNT_RECONFIRM".equals(current.getCardType())) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            long confirmedAmount = parseConfirmedAmount(action);
            if (current.getFocusedItemId() == null
                    || !matchesFocusedAmount(current, confirmedAmount)) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            next.setConfirmedAmount(confirmedAmount);
            next.setActive(false);
        } else if ("FINAL_ACCEPT".equals(action) || "FINAL_REJECT".equals(action)) {
            if (!"TRANSFER_READBACK".equals(current.getCardType())) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            next.setActive(false);
        } else {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        if (voiceInteractionCardMapper.replace(next) != 1) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private void confirmFinalTransfer(VoiceSessionVo session, String action) {
        if (!"FINAL_ACCEPT".equals(action) && !"FINAL_REJECT".equals(action)) {
            return;
        }
        if (session.getTransferId() == null
                || DialogueStep.valueOf(session.getCurrentStep()) != DialogueStep.WAITING_FINAL_APPROVAL) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        voiceTransferOrchestrator.confirm(
                UUID.fromString(session.getUserId()),
                UUID.fromString(session.getTransferId()),
                "FINAL_ACCEPT".equals(action));
    }

    private VoiceInteractionCardVo copyCard(VoiceInteractionCardVo current, String sourceTurnId) {
        VoiceInteractionCardVo next = new VoiceInteractionCardVo();
        next.setSessionId(current.getSessionId());
        next.setCardId(UUID.randomUUID().toString());
        next.setCardVersion(current.getCardVersion() + 1);
        next.setSourceTurnId(sourceTurnId);
        next.setCardType(current.getCardType());
        next.setActions(current.getActions());
        next.setCandidateItems(current.getCandidateItems());
        next.setFocusedItemId(current.getFocusedItemId());
        next.setConfirmedRecipientId(current.getConfirmedRecipientId());
        next.setConfirmedAmount(current.getConfirmedAmount());
        next.setActive(current.isActive());
        return next;
    }

    private long parseConfirmedAmount(String action) {
        try {
            long amount = Long.parseLong(action.substring("AMOUNT_ACCEPT:".length()));
            if (amount <= 0) {
                throw new NumberFormatException("amount must be positive");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private boolean matchesFocusedAmount(VoiceInteractionCardVo card, long amount) {
        try {
            for (JsonNode candidate : objectMapper.readTree(card.getCandidateItems())) {
                if (card.getFocusedItemId().equals(candidate.path("id").asText())
                        && candidate.path("amount").canConvertToLong()
                        && candidate.path("amount").longValue() == amount) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private void restoreTurnClaim(
            String userId, String sessionId, String previousStatus, RuntimeException originalException) {
        try {
            inTransaction(() -> {
                voiceSessionMapper.restoreTurnClaim(userId, sessionId, previousStatus);
                return null;
            });
        } catch (RuntimeException restorationException) {
            originalException.addSuppressed(restorationException);
        }
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    private VoiceSessionVo findOwnedSessionForTurn(String userId, String sessionId) {
        try {
            return voiceSessionMapper.findOwnedByIdForUpdate(userId, sessionId);
        } catch (RuntimeException exception) {
            if (isNoWaitLockFailure(exception)) {
                throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
            }
            throw exception;
        }
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

    private void rejectUnavailableSession(VoiceSessionVo voiceSession) {
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(voiceSession.getStatus());
        if (status == VoiceSessionStatus.CLOSED || status == VoiceSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private void expireSessionIfNeeded(String userId, String sessionId, VoiceSessionVo voiceSession) {
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(voiceSession.getStatus());
        LocalDateTime expiresAt = voiceSession.getExpiresAt();
        if (status == VoiceSessionStatus.CLOSED
                || status == VoiceSessionStatus.EXPIRED
                || (expiresAt != null && expiresAt.isAfter(LocalDateTime.now(clock)))) {
            return;
        }

        // EXPIRED는 세션 수명만 종료한다. 재진입·감사 시점의 대화 맥락을 보존하기 위해
        // currentStep은 AWAITING_INPUT으로 초기화하지 않고 만료 직전 단계를 유지한다.
        if (voiceSession.getTransferId() != null) {
            voiceTransferOrchestrator.cancelUnexecuted(
                    UUID.fromString(voiceSession.getUserId()),
                    UUID.fromString(voiceSession.getTransferId()));
        }
        voiceSessionMapper.updateStatusAndStep(
                userId, sessionId, VoiceSessionStatus.EXPIRED.name(), voiceSession.getCurrentStep());
        voiceInteractionCardMapper.deactivateActiveBySessionId(sessionId);
        voiceAdaptationSessionStateStore.clear(sessionId);
        voiceSession.setStatus(VoiceSessionStatus.EXPIRED.name());
    }

    private VoiceTurnResponse reuseOrRejectExistingTurn(
            DialogueTurnVo existingUserTurn, VoiceTurnRequest request) {
        if (!USER_SPEAKER.equals(existingUserTurn.getSpeaker()) || !isSameRequest(existingUserTurn, request)) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }

        DialogueTurnVo aiTurn = dialogueTurnMapper.findBySessionIdAndSequenceNo(
                existingUserTurn.getSessionId(), existingUserTurn.getSequenceNo() + 1);
        if (aiTurn == null || !AI_SPEAKER.equals(aiTurn.getSpeaker())) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
        return toStoredResponse(existingUserTurn.getSessionId(), existingUserTurn.getTurnId(), aiTurn);
    }

    private boolean isSameRequest(DialogueTurnVo dialogueTurn, VoiceTurnRequest request) {
        return Objects.equals(dialogueTurn.getTranscript(), request.getTranscript())
                && sameDecimal(dialogueTurn.getSttConfidence(), request.getSttConfidence())
                && Objects.equals(dialogueTurn.getInputType(), request.getInputType().name());
    }

    private boolean sameDecimal(BigDecimal first, BigDecimal second) {
        return first != null && second != null && first.compareTo(second) == 0;
    }

    private DialogueTurnVo userTurn(
            String sessionId,
            int sequenceNo,
            VoiceTurnRequest request,
            VoiceSessionVo voiceSession) {
        DialogueTurnVo dialogueTurn = commonTurn(
                request.getTurnId(), sessionId, sequenceNo, USER_SPEAKER, voiceSession.getCurrentStep());
        dialogueTurn.setTranscript(request.getTranscript());
        dialogueTurn.setSttConfidence(request.getSttConfidence());
        dialogueTurn.setInputType(request.getInputType().name());
        return dialogueTurn;
    }

    private DialogueTurnVo aiTurn(String sessionId, int sequenceNo, VoiceTurnAnalysisResult analysis) {
        DialogueTurnVo dialogueTurn = commonTurn(
                UUID.randomUUID().toString(), sessionId, sequenceNo, AI_SPEAKER, analysis.getNextStep().name());
        dialogueTurn.setIntent(analysis.getIntent().name());
        dialogueTurn.setTtsText(analysis.getTtsText());
        dialogueTurn.setTtsSsml(analysis.getTtsSsml());
        dialogueTurn.setDisplayCard(writeJson(analysis.getDisplayCard()));
        dialogueTurn.setExtractedSlots(writeStoredResponse(analysis));
        return dialogueTurn;
    }

    private DialogueTurnVo commonTurn(
            String turnId, String sessionId, int sequenceNo, String speaker, String step) {
        DialogueTurnVo dialogueTurn = new DialogueTurnVo();
        dialogueTurn.setTurnId(turnId);
        dialogueTurn.setSessionId(sessionId);
        dialogueTurn.setSequenceNo(sequenceNo);
        dialogueTurn.setSpeaker(speaker);
        dialogueTurn.setStep(step);
        dialogueTurn.setSilenceMs(0);
        dialogueTurn.setReplayCount(0);
        dialogueTurn.setInterrupted(false);
        return dialogueTurn;
    }

    private String writeStoredResponse(VoiceTurnAnalysisResult analysis) {
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set(SLOTS_FIELD, objectMapper.valueToTree(analysis.getSlots()));
        stored.set(CONFIDENCE_FIELD, objectMapper.valueToTree(analysis.getConfidence()));
        stored.put(NEXT_ACTION_FIELD, analysis.getNextAction().name());
        stored.put(REQUESTED_FUNCTION_FIELD, analysis.getRequestedFunction().name());
        stored.set(REQUIRED_SLOT_FIELD, analysis.getRequiredSlot());
        stored.set(DRAFT_SUMMARY_FIELD, analysis.getDraftSummary());
        return writeJson(stored);
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private VoiceTurnResponse toResponse(
            String sessionId, String turnId, VoiceTurnAnalysisResult analysis) {
        return new VoiceTurnResponse(
                sessionId,
                turnId,
                analysis.getNextStep(),
                analysis.getIntent().name(),
                analysis.getRequestedFunction().name(),
                analysis.getSlots(),
                analysis.getConfidence(),
                analysis.getTtsText(),
                analysis.getTtsSsml(),
                analysis.getDisplayCard(),
                analysis.getRequiredSlot(),
                analysis.getDraftSummary(),
                analysis.getNextAction().name());
    }

    private VoiceTurnResponse toStoredResponse(String sessionId, String turnId, DialogueTurnVo aiTurn) {
        try {
            JsonNode stored = objectMapper.readTree(aiTurn.getExtractedSlots());
            JsonNode slots = requiredObject(stored, SLOTS_FIELD);
            JsonNode confidence = stored.path(CONFIDENCE_FIELD);
            JsonNode requiredSlot = stored.get(REQUIRED_SLOT_FIELD);
            JsonNode draftSummary = stored.get(DRAFT_SUMMARY_FIELD);
            return new VoiceTurnResponse(
                    sessionId,
                    turnId,
                    DialogueStep.valueOf(aiTurn.getStep()),
                    aiTurn.getIntent(),
                    storedRequestedFunction(stored),
                    objectMapper.convertValue(slots, new TypeReference<Map<String, Object>>() {}),
                    confidence.decimalValue(),
                    aiTurn.getTtsText(),
                    aiTurn.getTtsSsml(),
                    readJson(aiTurn.getDisplayCard()),
                    requiredSlot,
                    draftSummary,
                    stored.path(NEXT_ACTION_FIELD).asText());
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private String storedRequestedFunction(JsonNode stored) {
        JsonNode value = stored.get(REQUESTED_FUNCTION_FIELD);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return VoiceRequestedFunction.NONE.name();
        }
        return VoiceRequestedFunction.valueOf(value.asText()).name();
    }

    private JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("Missing stored voice-turn slots.");
        }
        return value;
    }

    private JsonNode readJson(String value) throws JsonProcessingException {
        return value == null ? null : objectMapper.readTree(value);
    }

    private record TurnClaim(
            VoiceSessionVo voiceSession, DialogueTurnVo existingUserTurn, String previousStatus) {
        private static TurnClaim existing(VoiceSessionVo voiceSession, DialogueTurnVo existingUserTurn) {
            return new TurnClaim(voiceSession, existingUserTurn, null);
        }

        private static TurnClaim claimed(VoiceSessionVo voiceSession) {
            return new TurnClaim(voiceSession, null, voiceSession.getStatus());
        }
    }
}
