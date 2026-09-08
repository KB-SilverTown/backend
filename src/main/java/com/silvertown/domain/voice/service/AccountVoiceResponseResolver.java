package com.silvertown.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silvertown.domain.account.dto.AccountResponse;
import com.silvertown.domain.account.service.AccountService;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Resolves trusted account-domain results after an ACCOUNT_INQUIRY voice classification. */
@Component
@RequiredArgsConstructor
public class AccountVoiceResponseResolver {
    private static final String ACCOUNT_LIST_SCREEN = "ACCOUNT_LIST";

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public VoiceTurnAnalysisResult resolve(String userId, VoiceTurnAnalysisResult analysis) {
        if (analysis.getRequestedFunction() != VoiceRequestedFunction.ACCOUNT_INQUIRY) {
            return analysis;
        }

        try {
            List<AccountResponse> accounts = accountService.getAccounts(UUID.fromString(userId));
            return resolveAccounts(analysis, accounts);
        } catch (DataAccessException exception) {
            return queryFailed(analysis);
        }
    }

    private VoiceTurnAnalysisResult resolveAccounts(
            VoiceTurnAnalysisResult analysis, List<AccountResponse> accounts) {
        List<AccountResponse> safeAccounts = accounts == null ? List.of() : accounts;
        Map<String, Object> slots = new LinkedHashMap<>(analysis.getSlots());
        slots.put("accountCount", safeAccounts.size());

        if (safeAccounts.isEmpty()) {
            return resolved(
                    analysis,
                    slots,
                    "사용 중인 계좌가 없습니다. 계좌를 등록한 뒤 다시 확인해 주세요.",
                    emptyAccountCard());
        }

        return resolved(analysis, slots, accountTtsText(safeAccounts), accountListCard(safeAccounts));
    }

    private String accountTtsText(List<AccountResponse> accounts) {
        if (accounts.size() > 1) {
            return String.format(Locale.KOREA, "사용 중인 계좌는 %d개입니다. 계좌 목록을 화면에 보여드릴게요.", accounts.size());
        }

        AccountResponse account = accounts.get(0);
        String accountName = isBlank(account.getAccountName()) ? "계좌" : account.getAccountName();
        return String.format(Locale.KOREA, "%s의 잔액은 %,d원입니다.", accountName, account.getBalance());
    }

    private ObjectNode emptyAccountCard() {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "ACCOUNT_LIST");
        card.put("screenCode", ACCOUNT_LIST_SCREEN);
        card.put("totalCount", 0);
        card.putArray("actions");
        card.putArray("items");
        return card;
    }

    private ObjectNode accountListCard(List<AccountResponse> accounts) {
        ObjectNode card = emptyAccountCard();
        card.put("totalCount", accounts.size());
        ArrayNode items = (ArrayNode) card.path("items");
        for (AccountResponse account : accounts) {
            ObjectNode item = items.addObject();
            item.put("accountId", account.getAccountId().toString());
            item.put("bankCode", account.getBankCode());
            item.put("accountNumberMasked", account.getAccountNumberMasked());
            item.put("accountName", account.getAccountName());
            item.put("balance", account.getBalance());
            item.put("accountType", account.getAccountType());
        }
        return card;
    }

    private VoiceTurnAnalysisResult queryFailed(VoiceTurnAnalysisResult analysis) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("type", "ACCOUNT_QUERY_FAILED");
        card.putArray("actions");
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                analysis.getIntent(),
                new LinkedHashMap<>(analysis.getSlots()),
                analysis.getConfidence(),
                "계좌 정보를 불러오지 못했어요. 잠시 후 다시 말씀해 주세요.",
                "<speak>계좌 정보를 불러오지 못했어요. 잠시 후 다시 말씀해 주세요.</speak>",
                card,
                analysis.getRequiredSlot(),
                analysis.getDraftSummary(),
                VoiceNextAction.REASK_INPUT,
                VoiceRequestedFunction.NONE);
    }

    private VoiceTurnAnalysisResult resolved(
            VoiceTurnAnalysisResult analysis,
            Map<String, Object> slots,
            String ttsText,
            JsonNode displayCard) {
        return new VoiceTurnAnalysisResult(
                analysis.getNextStep(),
                analysis.getIntent(),
                slots,
                analysis.getConfidence(),
                ttsText,
                "<speak>" + ttsText + "</speak>",
                displayCard,
                analysis.getRequiredSlot(),
                objectMapper.valueToTree(slots),
                analysis.getNextAction(),
                analysis.getRequestedFunction());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
