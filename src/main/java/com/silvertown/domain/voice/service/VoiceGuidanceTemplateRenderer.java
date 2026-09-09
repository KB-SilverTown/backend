package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Applies the approved wording variants without changing server-verified financial values. */
@Component
public class VoiceGuidanceTemplateRenderer {
    /**
     * Renders voice guidance without a subsequent action.
     *
     * @param ttsText     the original voice guidance text
     * @param displayCard the display-card data used to select specialized wording
     * @param mode        the voice guidance mode
     * @return the rendered voice guidance text
     */
    public String render(String ttsText, JsonNode displayCard, VoiceGuidanceMode mode) {
        return render(ttsText, displayCard, mode, null);
    }

    /**
     * Renders approved voice guidance for the specified display card and next action.
     *
     * @param ttsText    the original voice guidance text
     * @param displayCard the display card containing context for specialized guidance
     * @param mode       the voice guidance mode
     * @param nextAction the action associated with the guidance
     * @return the rendered guidance text, or the original text when no specialized wording applies
     */
    public String render(
            String ttsText, JsonNode displayCard, VoiceGuidanceMode mode, VoiceNextAction nextAction) {
        if (ttsText == null || mode == VoiceGuidanceMode.STANDARD) {
            return ttsText;
        }
        String type = displayCard == null ? "" : displayCard.path("type").asText();
        if ("RECIPIENT_CANDIDATES".equals(type) && nextAction == VoiceNextAction.ASK_RECIPIENT) {
            return recipient(displayCard, ttsText, mode);
        }
        if ("AMOUNT_RECONFIRM".equals(type) && nextAction == VoiceNextAction.RECONFIRM_INPUT) {
            return amount(displayCard, ttsText, mode);
        }
        if ("TRANSFER_READBACK".equals(type) && nextAction == VoiceNextAction.ASK_FINAL_APPROVAL) {
            return readback(displayCard, ttsText, mode);
        }
        return mode == VoiceGuidanceMode.SUPPORT && isReask(ttsText)
                ? "잘 듣지 못했어요. 천천히, 짧게 다시 말씀해 주세요."
                : ttsText;
    }

    /**
     * Creates a recipient confirmation prompt for the selected recipient.
     *
     * @param card     the card containing recipient candidates and focus information
     * @param fallback the text to use when no recipient display name is available
     * @param mode     the voice guidance mode that determines the prompt wording
     * @return the recipient confirmation prompt, or {@code fallback} when no display name is available
     */
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

    /**
     * Creates a mode-specific confirmation prompt for the transfer amount.
     *
     * @param card     the display card containing the amount
     * @param fallback the text returned when the amount is unavailable or invalid
     * @param mode     the voice guidance mode
     * @return         a confirmation prompt containing the formatted amount, or the fallback text
     */
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

    /**
     * Creates a transfer approval prompt using the recipient and amount from the card.
     *
     * @param card     the card containing the recipient name and transfer amount
     * @param fallback  the text to return when required card data is missing or invalid
     * @param mode      the voice guidance mode
     * @return          the formatted transfer prompt, or the fallback text
     */
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

    /**
     * Determines whether the text contains a supported Korean re-ask phrase.
     *
     * @param value the text to inspect
     * @return {@code true} if the text contains a re-ask phrase, {@code false} otherwise
     */
    private boolean isReask(String value) {
        return value.contains("다시 말씀") || value.contains("다시 말");
    }
}
