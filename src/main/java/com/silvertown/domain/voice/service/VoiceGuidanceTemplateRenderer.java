package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Applies the approved wording variants without changing server-verified financial values. */
@Component
public class VoiceGuidanceTemplateRenderer {
    public String render(String ttsText, JsonNode displayCard, VoiceGuidanceMode mode) {
        if (ttsText == null || mode == VoiceGuidanceMode.STANDARD) {
            return ttsText;
        }
        String type = displayCard == null ? "" : displayCard.path("type").asText();
        return switch (type) {
            case "RECIPIENT_CANDIDATES" -> recipient(displayCard, ttsText, mode);
            case "AMOUNT_RECONFIRM" -> amount(displayCard, ttsText, mode);
            case "TRANSFER_READBACK" -> readback(displayCard, ttsText, mode);
            default -> mode == VoiceGuidanceMode.SUPPORT && isReask(ttsText)
                    ? "잘 듣지 못했어요. 천천히, 짧게 다시 말씀해 주세요."
                    : ttsText;
        };
    }

    private String recipient(JsonNode card, String fallback, VoiceGuidanceMode mode) {
        String focusedItemId = card.path("focusedItemId").asText();
        JsonNode selected = card.path("items").path(0);
        for (JsonNode item : card.path("items")) {
            if (item.path("isFocused").asBoolean()
                    || (!focusedItemId.isBlank() && focusedItemId.equals(item.path("id").asText()))) {
                selected = item;
                break;
            }
        }
        String name = selected.path("displayName").asText();
        if (name.isBlank()) {
            return fallback;
        }
        return mode == VoiceGuidanceMode.SUPPORT
                ? "받는 분은 " + name + "님입니다. 맞으면 네라고 말씀해 주세요."
                : name + "님이 맞을까요?";
    }

    private String amount(JsonNode card, String fallback, VoiceGuidanceMode mode) {
        JsonNode amount = card.has("amount") ? card.path("amount") : card.path("amountCandidates").path(0);
        if (!amount.canConvertToLong()) {
            return fallback;
        }
        String formatted = String.format(Locale.KOREA, "%,d원", amount.longValue());
        return mode == VoiceGuidanceMode.SUPPORT
                ? "금액은 " + formatted + "입니다. 맞으면 네라고 말씀해 주세요."
                : formatted + "이 맞을까요?";
    }

    private String readback(JsonNode card, String fallback, VoiceGuidanceMode mode) {
        String recipient = card.path("recipientName").asText();
        JsonNode amount = card.path("amount");
        if (recipient.isBlank() || !amount.canConvertToLong()) {
            return fallback;
        }
        String formatted = String.format(Locale.KOREA, "%,d원", amount.longValue());
        return mode == VoiceGuidanceMode.SUPPORT
                ? "받는 분은 " + recipient + "님입니다. 금액은 " + formatted
                        + "입니다. 돈을 보내시려면 네라고 말씀해 주세요."
                : recipient + "님에게 " + formatted + "을 보내시겠어요?";
    }

    private boolean isReask(String value) {
        return value.contains("다시 말씀") || value.contains("다시 말");
    }
}
