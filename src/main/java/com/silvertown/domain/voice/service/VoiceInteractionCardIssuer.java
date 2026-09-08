package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.vo.VoiceInteractionCardVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoiceInteractionCardIssuer {
    private final VoiceInteractionCardMapper voiceInteractionCardMapper;
    private final ObjectMapper objectMapper;

    public JsonNode issueIfInteractive(String sessionId, String sourceTurnId, JsonNode displayCard) {
        if (displayCard == null || !displayCard.isObject()) {
            return displayCard;
        }
        String type = displayCard.path("type").asText();
        if (!"RECIPIENT_CANDIDATES".equals(type) && !"AMOUNT_RECONFIRM".equals(type)
                && !"TRANSFER_READBACK".equals(type) && !"TRANSFER_RISK_CHECK".equals(type)
                && !"TRANSFER_HELD".equals(type)) {
            return displayCard;
        }
        VoiceInteractionCardVo previous = voiceInteractionCardMapper.findBySessionIdForUpdate(sessionId);
        ArrayNode items = items(type, displayCard);
        if (items.isEmpty() && ("RECIPIENT_CANDIDATES".equals(type)
                || "AMOUNT_RECONFIRM".equals(type))) {
            return displayCard;
        }
        VoiceInteractionCardVo card = new VoiceInteractionCardVo();
        card.setSessionId(sessionId);
        card.setCardId(UUID.randomUUID().toString());
        card.setCardVersion(previous == null ? 1 : previous.getCardVersion() + 1);
        card.setSourceTurnId(sourceTurnId);
        card.setCardType(type);
        card.setActions(actions(type).toString());
        card.setCandidateItems(items.toString());
        card.setFocusedItemId(initialFocusedItemId(displayCard, items));
        if ("RECIPIENT_CANDIDATES".equals(type)) {
            card.setConfirmedRecipientId(null);
        } else if (previous != null) {
            card.setConfirmedRecipientId(previous.getConfirmedRecipientId());
        }
        if (!"RECIPIENT_CANDIDATES".equals(type)
                && !"AMOUNT_RECONFIRM".equals(type)
                && previous != null) {
            card.setConfirmedAmount(previous.getConfirmedAmount());
        }
        card.setActive(true);
        if (previous == null) {
            voiceInteractionCardMapper.insert(card);
        } else {
            voiceInteractionCardMapper.replace(card);
        }
        ObjectNode rendered = (ObjectNode) render(card);
        if ("TRANSFER_READBACK".equals(type) || "TRANSFER_RISK_CHECK".equals(type)
                || "TRANSFER_HELD".equals(type)) {
            displayCard.fields().forEachRemaining(entry -> {
                if (!rendered.has(entry.getKey())) {
                    rendered.set(entry.getKey(), entry.getValue());
                }
            });
        }
        return rendered;
    }

    private ArrayNode items(String type, JsonNode displayCard) {
        ArrayNode result = objectMapper.createArrayNode();
        if ("RECIPIENT_CANDIDATES".equals(type)) {
            for (JsonNode item : displayCard.path("items")) {
                if (!item.isObject()) {
                    continue;
                }
                ObjectNode copy = item.deepCopy();
                String id = copy.path("id").asText(copy.path("recipientId").asText());
                if (id.isBlank()) {
                    continue;
                }
                copy.put("id", id);
                copy.remove("isFocused");
                result.add(copy);
            }
            return result;
        }
        for (JsonNode amount : displayCard.path("amountCandidates")) {
            if (!amount.canConvertToLong() || amount.longValue() <= 0) {
                continue;
            }
            ObjectNode item = result.addObject();
            item.put("id", UUID.randomUUID().toString());
            item.put("amount", amount.longValue());
            item.put("label", String.format("%,d원", amount.longValue()));
        }
        return result;
    }

    private ArrayNode actions(String type) {
        ArrayNode actions = objectMapper.createArrayNode();
        if ("TRANSFER_READBACK".equals(type) || "TRANSFER_RISK_CHECK".equals(type)
                || "TRANSFER_HELD".equals(type)) {
            return actions;
        }
        actions.add("RECIPIENT_CANDIDATES".equals(type) ? "SELECT_RECIPIENT" : "SELECT_AMOUNT");
        actions.add("ACCEPT_FOCUSED_SELECTION");
        actions.add("REJECT_FOCUSED_SELECTION");
        actions.add("CANCEL_FLOW");
        return actions;
    }

    private String initialFocusedItemId(JsonNode displayCard, ArrayNode items) {
        JsonNode requestedFocus = displayCard.get("focusedItemId");
        if (requestedFocus != null) {
            if (!requestedFocus.isTextual()) {
                return null;
            }
            String focusedItemId = requestedFocus.asText();
            for (JsonNode item : items) {
                if (focusedItemId.equals(item.path("id").asText())) {
                    return focusedItemId;
                }
            }
            return null;
        }
        if (items.isEmpty()) {
            return null;
        }
        String firstItemId = items.get(0).path("id").asText();
        return firstItemId.isBlank() ? null : firstItemId;
    }

    private JsonNode render(VoiceInteractionCardVo card) {
        try {
            ObjectNode display = objectMapper.createObjectNode();
            display.put("type", card.getCardType());
            display.put("cardId", card.getCardId());
            display.put("cardVersion", card.getCardVersion());
            display.put("focusedItemId", card.getFocusedItemId());
            display.set("actions", objectMapper.readTree(card.getActions()));
            ArrayNode items = display.putArray("items");
            for (JsonNode item : objectMapper.readTree(card.getCandidateItems())) {
                ObjectNode copy = item.deepCopy();
                copy.put("isFocused", Objects.equals(card.getFocusedItemId(), copy.path("id").asText()));
                items.add(copy);
            }
            return display;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
