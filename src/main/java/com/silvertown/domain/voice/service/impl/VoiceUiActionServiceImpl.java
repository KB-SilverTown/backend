package com.silvertown.domain.voice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.transfer.dto.AmountValidationResponse;
import com.silvertown.domain.transfer.dto.TransferResponse;
import com.silvertown.domain.voice.dto.VoiceUiActionRequest;
import com.silvertown.domain.voice.dto.VoiceUiActionResponse;
import com.silvertown.domain.voice.adaptation.VoiceAdaptationSessionStateStore;
import com.silvertown.domain.voice.adaptation.VoiceAdaptationPolicy;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.enums.VoiceUiActionType;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.mapper.VoiceUiActionMapper;
import com.silvertown.domain.voice.service.VoiceSsmlRenderer;
import com.silvertown.domain.voice.service.VoiceGuidanceTemplateRenderer;
import com.silvertown.domain.voice.service.VoiceGuidanceSettingsService;
import com.silvertown.domain.voice.service.VoiceTransferOrchestrator;
import com.silvertown.domain.voice.service.VoiceTransferPreparation;
import com.silvertown.domain.voice.service.VoiceUiActionService;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.domain.voice.vo.VoiceUiActionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class VoiceUiActionServiceImpl implements VoiceUiActionService {
    private static final String AI_SPEAKER = "AI";

    private final VoiceSessionMapper voiceSessionMapper;
    private final VoiceInteractionCardMapper voiceInteractionCardMapper;
    private final VoiceUiActionMapper voiceUiActionMapper;
    private final DialogueTurnMapper dialogueTurnMapper;
    private final VoiceTransferOrchestrator voiceTransferOrchestrator;
    private final VoiceSsmlRenderer voiceSsmlRenderer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final VoiceAdaptationSessionStateStore voiceAdaptationSessionStateStore;
    private final VoiceGuidanceTemplateRenderer voiceGuidanceTemplateRenderer;
    private final VoiceGuidanceSettingsService voiceGuidanceSettingsService;

    /** Compatibility constructor retained for focused tests that do not load Spring. */
    public VoiceUiActionServiceImpl(
            VoiceSessionMapper voiceSessionMapper,
            VoiceInteractionCardMapper voiceInteractionCardMapper,
            VoiceUiActionMapper voiceUiActionMapper,
            DialogueTurnMapper dialogueTurnMapper,
            VoiceTransferOrchestrator voiceTransferOrchestrator,
            VoiceSsmlRenderer voiceSsmlRenderer,
            ObjectMapper objectMapper,
            Clock clock) {
        this(voiceSessionMapper, voiceInteractionCardMapper, voiceUiActionMapper, dialogueTurnMapper,
                voiceTransferOrchestrator, voiceSsmlRenderer, objectMapper, clock,
                new VoiceAdaptationSessionStateStore(new VoiceAdaptationPolicy()),
                new VoiceGuidanceTemplateRenderer(), new VoiceGuidanceSettingsService(null));
    }

    @Override
    @Transactional
    public VoiceUiActionResponse process(String userId, String sessionId, VoiceUiActionRequest request) {
        String requestHash = requestHash(request);
        VoiceSessionVo session = voiceSessionMapper.findOwnedByIdForUpdate(userId, sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND);
        }
        VoiceUiActionVo existing = voiceUiActionMapper.findByActionId(request.getActionId());
        if (existing != null) {
            return reuseOrReject(existing, sessionId, requestHash);
        }
        rejectUnavailable(session);

        VoiceInteractionCardVo card = voiceInteractionCardMapper.findBySessionIdForUpdate(sessionId);
        validateCurrentCard(card, request);
        validateAction(card, request);

        String responseTurnId = UUID.randomUUID().toString();
        ActionOutcome outcome = outcome(userId, session, card, request, responseTurnId);
        DialogueTurnVo responseTurn = aiTurn(userId, sessionId, responseTurnId, outcome);
        dialogueTurnMapper.insert(responseTurn);

        if (outcome.card() != null) {
            replaceCard(outcome.card());
        }
        if (outcome.closeSession()) {
            voiceSessionMapper.closeOwned(
                    userId, sessionId, DialogueStep.CANCELLED.name(), LocalDateTime.now(clock));
            voiceGuidanceSettingsService.completeSession(
                    userId, voiceAdaptationSessionStateStore.stateOf(sessionId));
            voiceAdaptationSessionStateStore.clear(sessionId);
        } else if (voiceSessionMapper.updateStatusAndStep(
                userId, sessionId, VoiceSessionStatus.SPEAKING.name(), outcome.state().name()) != 1) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }

        VoiceUiActionVo action = new VoiceUiActionVo();
        action.setActionId(request.getActionId());
        action.setSessionId(sessionId);
        action.setSourceTurnId(request.getSourceTurnId());
        action.setCardId(request.getCardId());
        action.setCardVersion(request.getCardVersion());
        action.setActionType(request.getActionType().name());
        action.setItemId(request.getItemId());
        action.setRequestHash(requestHash);
        action.setResponseTurnId(responseTurnId);
        voiceUiActionMapper.insert(action);
        VoiceUiActionResponse response = storedResponse(sessionId, request.getActionId(), responseTurn);
        if (response.getTtsText() != null) {
            voiceAdaptationSessionStateStore.responseRendered(
                    sessionId, DialogueStep.valueOf(session.getCurrentStep()));
        }
        return response;
    }

    private VoiceUiActionResponse reuseOrReject(
            VoiceUiActionVo action, String sessionId, String requestHash) {
        if (!sessionId.equals(action.getSessionId()) || !requestHash.equals(action.getRequestHash())) {
            throw new BusinessException(ErrorCode.VOICE_UI_ACTION_CONFLICT);
        }
        DialogueTurnVo responseTurn = dialogueTurnMapper.findBySessionIdAndTurnId(
                sessionId, action.getResponseTurnId());
        if (responseTurn == null || !AI_SPEAKER.equals(responseTurn.getSpeaker())) {
            throw new BusinessException(ErrorCode.VOICE_UI_ACTION_CONFLICT);
        }
        return storedResponse(sessionId, action.getActionId(), responseTurn);
    }

    private ActionOutcome outcome(
            String userId,
            VoiceSessionVo session,
            VoiceInteractionCardVo card,
            VoiceUiActionRequest request,
            String responseTurnId) {
        return switch (request.getActionType()) {
            case SELECT_RECIPIENT, SELECT_AMOUNT -> select(card, request, responseTurnId);
            case ACCEPT_FOCUSED_SELECTION -> accept(userId, session, card, responseTurnId);
            case REJECT_FOCUSED_SELECTION -> reject(card, responseTurnId);
            case CANCEL_FLOW -> cancel(card, responseTurnId);
        };
    }

    private ActionOutcome select(
            VoiceInteractionCardVo current, VoiceUiActionRequest request, String responseTurnId) {
        VoiceInteractionCardVo card = nextCard(current, responseTurnId);
        card.setFocusedItemId(request.getItemId());
        return new ActionOutcome(
                DialogueStep.valueOf(currentStepFor(current.getCardType())),
                null,
                null,
                displayCard(card),
                null,
                VoiceNextAction.RECONFIRM_INPUT.name(),
                card,
                false);
    }

    private ActionOutcome accept(
            String userId, VoiceSessionVo session, VoiceInteractionCardVo current, String responseTurnId) {
        if ("RECIPIENT_CANDIDATES".equals(current.getCardType())) {
            VoiceInteractionCardVo card = nextCard(current, responseTurnId);
            card.setConfirmedRecipientId(card.getFocusedItemId());
            card.setActive(false);
            List<Long> pendingAmountCandidates = pendingAmountCandidates(card);
            if (!pendingAmountCandidates.isEmpty()) {
                return reaskAmount(card, pendingAmountCandidates);
            }
            return new ActionOutcome(
                    DialogueStep.AWAITING_AMOUNT,
                    "TRANSFER",
                    "보낼 금액을 말씀해 주세요.",
                    null,
                    null,
                    VoiceNextAction.ASK_AMOUNT.name(),
                    card,
                    false);
        }
        if ("AMOUNT_RECONFIRM".equals(current.getCardType())) {
            VoiceInteractionCardVo card = nextCard(current, responseTurnId);
            card.setConfirmedAmount(focusedAmount(card));
            card.setActive(false);
            return prepareAndAssess(userId, session, card, responseTurnId);
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private ActionOutcome prepareAndAssess(
            String userId, VoiceSessionVo session, VoiceInteractionCardVo card, String responseTurnId) {
        if (session.getFromAccountId() == null || card.getConfirmedRecipientId() == null
                || card.getConfirmedAmount() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        VoiceTransferPreparation preparation = voiceTransferOrchestrator.prepareAndAssess(
                UUID.fromString(userId),
                UUID.fromString(session.getSessionId()),
                UUID.fromString(session.getFromAccountId()),
                UUID.fromString(card.getConfirmedRecipientId()),
                card.getConfirmedAmount());
        AmountValidationResponse validation = preparation.amountValidation();
        if (validation.isAmountReconfirmRequired()
                || validation.getConfirmedAmount() == null
                || !card.getConfirmedAmount().equals(validation.getConfirmedAmount())) {
            return reaskAmount(card, validation.getAmountCandidates());
        }
        var risk = preparation.risk();
        var canonicalReadback = preparation.readback();
        if (!risk.isAdditionalCheckRequired()) {
            VoiceInteractionCardVo readbackCard = cardFor(
                    session.getSessionId(), responseTurnId, "TRANSFER_READBACK", "[]", "[]", null,
                    card.getConfirmedRecipientId(), card.getConfirmedAmount(), true, card.getCardVersion() + 1);
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("transferId", canonicalReadback.getTransferId().toString());
            summary.put("recipientName", canonicalReadback.getRecipient().getDisplayName());
            summary.put("amount", canonicalReadback.getAmount());
            return new ActionOutcome(
                    DialogueStep.WAITING_FINAL_APPROVAL,
                    "TRANSFER",
                    canonicalReadback.getConfirmationText(),
                    displayTransferCard(readbackCard, canonicalReadback),
                    summary,
                    VoiceNextAction.ASK_FINAL_APPROVAL.name(),
                    readbackCard,
                    false);
        }
        boolean held = "HOLD".equals(risk.getRecommendedAction())
                || "HIGH".equals(risk.getLevel()) || "CRITICAL".equals(risk.getLevel());
        VoiceInteractionCardVo riskCard = cardFor(
                session.getSessionId(), responseTurnId, held ? "TRANSFER_HELD" : "TRANSFER_RISK_CHECK",
                "[]", "[]", null, card.getConfirmedRecipientId(), card.getConfirmedAmount(), true,
                card.getCardVersion() + 1);
        return new ActionOutcome(
                held ? DialogueStep.HELD : DialogueStep.RISK_CHECK,
                "TRANSFER",
                held ? "안전을 위해 송금을 잠시 확인할게요." : "안전을 위해 돈을 보내는 이유를 말씀해 주세요.",
                displayTransferCard(riskCard, canonicalReadback),
                null,
                held ? VoiceNextAction.WAIT_GUARDIAN_VERIFICATION.name() : VoiceNextAction.NONE.name(),
                riskCard,
                false);
    }

    private ActionOutcome reaskAmount(VoiceInteractionCardVo card, List<Long> amountCandidates) {
        if (amountCandidates == null || amountCandidates.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        ArrayNode items = objectMapper.createArrayNode();
        String focusedItemId = null;
        for (Long amount : amountCandidates) {
            if (amount == null || amount <= 0) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            ObjectNode item = items.addObject();
            String itemId = UUID.randomUUID().toString();
            item.put("id", itemId);
            item.put("amount", amount);
            item.put("label", String.format("%,d원", amount));
            if (focusedItemId == null) {
                focusedItemId = itemId;
            }
        }
        card.setCardType("AMOUNT_RECONFIRM");
        card.setActions("[\"SELECT_AMOUNT\",\"ACCEPT_FOCUSED_SELECTION\",\"REJECT_FOCUSED_SELECTION\",\"CANCEL_FLOW\"]");
        card.setCandidateItems(items.toString());
        card.setFocusedItemId(focusedItemId);
        card.setConfirmedAmount(null);
        card.setActive(true);
        return new ActionOutcome(
                DialogueStep.RECONFIRMING,
                "TRANSFER",
                String.format("보낼 금액은 %,d원이 맞을까요?", amountCandidates.get(0)),
                displayCard(card),
                null,
                VoiceNextAction.RECONFIRM_INPUT.name(),
                card,
                false);
    }

    private List<Long> pendingAmountCandidates(VoiceInteractionCardVo card) {
        try {
            ArrayNode items = candidateItems(card);
            if (items.isEmpty()) {
                return List.of();
            }
            JsonNode candidates = items.get(0).path("pendingAmountCandidates");
            if (!candidates.isArray()) {
                return List.of();
            }
            List<Long> result = new java.util.ArrayList<>();
            for (JsonNode candidate : candidates) {
                if (!candidate.canConvertToLong() || candidate.longValue() <= 0) {
                    return List.of();
                }
                result.add(candidate.longValue());
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ActionOutcome reject(VoiceInteractionCardVo current, String responseTurnId) {
        VoiceInteractionCardVo card = nextCard(current, responseTurnId);
        card.setFocusedItemId(null);
        return new ActionOutcome(
                DialogueStep.valueOf(currentStepFor(current.getCardType())),
                "TRANSFER",
                "화면에서 다시 골라 주세요.",
                displayCard(card),
                null,
                VoiceNextAction.RECONFIRM_INPUT.name(),
                card,
                false);
    }

    private ActionOutcome cancel(VoiceInteractionCardVo current, String responseTurnId) {
        VoiceInteractionCardVo card = nextCard(current, responseTurnId);
        card.setActive(false);
        return new ActionOutcome(
                DialogueStep.CANCELLED,
                "TRANSFER",
                "돈 보내기를 취소했어요.",
                null,
                null,
                VoiceNextAction.END_SESSION.name(),
                card,
                true);
    }

    private void validateCurrentCard(VoiceInteractionCardVo card, VoiceUiActionRequest request) {
        if (card == null || !card.isActive() || !request.getSourceTurnId().equals(card.getSourceTurnId())
                || !request.getCardId().equals(card.getCardId())
                || request.getCardVersion() != card.getCardVersion()) {
            throw new BusinessException(ErrorCode.VOICE_CARD_STALE);
        }
    }

    private void validateAction(VoiceInteractionCardVo card, VoiceUiActionRequest request) {
        List<String> actions = readActions(card);
        if (!actions.contains(request.getActionType().name())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        boolean selection = request.getActionType() == VoiceUiActionType.SELECT_RECIPIENT
                || request.getActionType() == VoiceUiActionType.SELECT_AMOUNT;
        if (selection != (request.getItemId() != null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (selection && !hasCandidate(card, request.getItemId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getActionType() == VoiceUiActionType.SELECT_RECIPIENT
                && !"RECIPIENT_CANDIDATES".equals(card.getCardType())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getActionType() == VoiceUiActionType.SELECT_AMOUNT
                && !"AMOUNT_RECONFIRM".equals(card.getCardType())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getActionType() == VoiceUiActionType.ACCEPT_FOCUSED_SELECTION
                && card.getFocusedItemId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private VoiceInteractionCardVo nextCard(VoiceInteractionCardVo current, String sourceTurnId) {
        return cardFor(
                current.getSessionId(), sourceTurnId, current.getCardType(), current.getActions(),
                current.getCandidateItems(), current.getFocusedItemId(), current.getConfirmedRecipientId(),
                current.getConfirmedAmount(), current.isActive(), current.getCardVersion() + 1);
    }

    private VoiceInteractionCardVo cardFor(
            String sessionId, String sourceTurnId, String type, String actions, String items, String focusedItemId,
            String confirmedRecipientId, Long confirmedAmount, boolean active, int version) {
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(sessionId);
        card.setCardId(UUID.randomUUID().toString());
        card.setCardVersion(version);
        card.setSourceTurnId(sourceTurnId);
        card.setCardType(type);
        card.setActions(actions);
        card.setCandidateItems(items);
        card.setFocusedItemId(focusedItemId);
        card.setConfirmedRecipientId(confirmedRecipientId);
        card.setConfirmedAmount(confirmedAmount);
        card.setActive(active);
        return card;
    }

    private void replaceCard(VoiceInteractionCardVo card) {
        if (voiceInteractionCardMapper.replace(card) != 1) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private DialogueTurnVo aiTurn(
            String userId, String sessionId, String turnId, ActionOutcome outcome) {
        DialogueTurnVo turn = new DialogueTurnVo();
        turn.setTurnId(turnId);
        turn.setSessionId(sessionId);
        turn.setSequenceNo(dialogueTurnMapper.findNextSequenceNo(sessionId));
        turn.setSpeaker(AI_SPEAKER);
        turn.setStep(outcome.state().name());
        turn.setIntent(outcome.intent());
        String renderedText = renderedText(sessionId, outcome);
        turn.setTtsText(renderedText);
        turn.setTtsSsml(renderedText == null ? null : renderSsml(userId, renderedText, sessionId));
        turn.setDisplayCard(writeJson(outcome.displayCard()));
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set("slots", objectMapper.createObjectNode());
        stored.put("nextAction", outcome.nextAction());
        stored.put("requestedFunction", "NONE");
        stored.set("draftSummary", outcome.draftSummary());
        turn.setExtractedSlots(writeJson(stored));
        turn.setReplayCount(0);
        turn.setInterrupted(false);
        return turn;
    }

    private String renderedText(String sessionId, ActionOutcome outcome) {
        var state = voiceAdaptationSessionStateStore.stateOf(sessionId);
        var mode = state == null ? com.silvertown.domain.voice.enums.VoiceGuidanceMode.STANDARD : state.mode();
        return voiceGuidanceTemplateRenderer.render(
                outcome.ttsText(), outcome.displayCard(), mode, VoiceNextAction.valueOf(outcome.nextAction()));
    }

    private String renderSsml(String userId, String text, String sessionId) {
        var state = voiceAdaptationSessionStateStore.stateOf(sessionId);
        var mode = state == null ? com.silvertown.domain.voice.enums.VoiceGuidanceMode.STANDARD : state.mode();
        return mode == com.silvertown.domain.voice.enums.VoiceGuidanceMode.STANDARD
                ? voiceSsmlRenderer.render(userId, text)
                : voiceSsmlRenderer.render(userId, text, mode);
    }

    private VoiceUiActionResponse storedResponse(String sessionId, String actionId, DialogueTurnVo turn) {
        try {
            JsonNode stored = objectMapper.readTree(turn.getExtractedSlots());
            return new VoiceUiActionResponse(
                    sessionId, actionId, turn.getTurnId(), DialogueStep.valueOf(turn.getStep()), turn.getIntent(),
                    stored.path("requestedFunction").asText("NONE"), Map.of(), null,
                    turn.getTtsText(), turn.getTtsSsml(), readJson(turn.getDisplayCard()), null,
                    stored.get("draftSummary"), stored.path("nextAction").asText());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VOICE_UI_ACTION_CONFLICT);
        }
    }

    private JsonNode displayCard(VoiceInteractionCardVo card) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("type", card.getCardType());
            result.put("cardId", card.getCardId());
            result.put("cardVersion", card.getCardVersion());
            result.set("actions", objectMapper.readTree(card.getActions()));
            ArrayNode items = candidateItems(card);
            ArrayNode rendered = result.putArray("items");
            for (JsonNode item : items) {
                ObjectNode copy = item.deepCopy();
                copy.put("isFocused", item.path("id").asText().equals(card.getFocusedItemId()));
                rendered.add(copy);
            }
            if (card.getFocusedItemId() != null) {
                result.put("focusedItemId", card.getFocusedItemId());
            }
            if (card.getConfirmedRecipientId() != null) {
                result.put("confirmedRecipientId", card.getConfirmedRecipientId());
            }
            if (card.getConfirmedAmount() != null) {
                result.put("confirmedAmount", card.getConfirmedAmount());
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonNode displayTransferCard(VoiceInteractionCardVo card, TransferResponse transfer) {
        ObjectNode display = (ObjectNode) displayCard(card);
        display.put("transferId", transfer.getTransferId().toString());
        display.put("recipientName", transfer.getRecipient().getDisplayName());
        display.put("amount", transfer.getAmount());
        return display;
    }

    private boolean hasCandidate(VoiceInteractionCardVo card, String itemId) {
        try {
            for (JsonNode item : candidateItems(card)) {
                if (itemId.equals(item.path("id").asText())) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private long focusedAmount(VoiceInteractionCardVo card) {
        try {
            for (JsonNode item : candidateItems(card)) {
                if (card.getFocusedItemId().equals(item.path("id").asText()) && item.path("amount").canConvertToLong()) {
                    return item.path("amount").longValue();
                }
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> readActions(VoiceInteractionCardVo card) {
        try {
            return objectMapper.readValue(card.getActions(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ArrayNode candidateItems(VoiceInteractionCardVo card) throws JsonProcessingException {
        if (card.getCandidateItems() == null || card.getCandidateItems().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        JsonNode items = objectMapper.readTree(card.getCandidateItems());
        if (items == null || !items.isArray()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        for (JsonNode item : items) {
            if (!item.isObject()) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
        return (ArrayNode) items;
    }

    private String currentStepFor(String cardType) {
        return "AMOUNT_RECONFIRM".equals(cardType)
                ? DialogueStep.RECONFIRMING.name() : DialogueStep.AWAITING_RECIPIENT.name();
    }

    private void rejectUnavailable(VoiceSessionVo session) {
        VoiceSessionStatus status = VoiceSessionStatus.valueOf(session.getStatus());
        if (status == VoiceSessionStatus.CLOSED || status == VoiceSessionStatus.EXPIRED
                || status == VoiceSessionStatus.PROCESSING
                || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private String requestHash(VoiceUiActionRequest request) {
        String value = String.join("|", request.getSourceTurnId(), request.getCardId(),
                String.valueOf(request.getCardVersion()), request.getActionType().name(),
                request.getItemId() == null ? "" : request.getItemId());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte valueByte : digest) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
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

    private JsonNode readJson(String value) throws JsonProcessingException {
        return value == null ? null : objectMapper.readTree(value);
    }

    private record ActionOutcome(
            DialogueStep state,
            String intent,
            String ttsText,
            JsonNode displayCard,
            JsonNode draftSummary,
            String nextAction,
            VoiceInteractionCardVo card,
            boolean closeSession) {}
}
