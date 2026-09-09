package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.account.dto.AccountResponse;
import com.silvertown.domain.account.service.AccountService;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceIntent;
import com.silvertown.domain.voice.enums.VoiceNextAction;
import com.silvertown.domain.voice.enums.VoiceRequestedFunction;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AccountVoiceResponseResolverTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private AccountService accountService;
    private AccountVoiceResponseResolver resolver;

    @BeforeEach
    void setUp() {
        accountService = org.mockito.Mockito.mock(AccountService.class);
        resolver = new AccountVoiceResponseResolver(accountService, new ObjectMapper());
    }

    @Test
    void resolvesOneAccountWithBalanceAndTrustedCardFields() {
        AccountResponse account = account("생활비 통장", 48200L);
        when(accountService.getAccounts(UUID.fromString(USER_ID))).thenReturn(List.of(account));

        VoiceTurnAnalysisResult result = resolver.resolve(USER_ID, accountInquiry());

        assertEquals("생활비 통장의 잔액은 48,200원입니다.", result.getTtsText());
        assertEquals("ACCOUNT_LIST", result.getDisplayCard().path("type").asText());
        assertEquals("ACCOUNT_LIST", result.getDisplayCard().path("screenCode").asText());
        assertEquals(1, result.getDisplayCard().path("totalCount").asInt());
        assertEmptyActions(result.getDisplayCard());
        assertEquals(account.getAccountId().toString(), result.getDisplayCard().path("items").get(0)
                .path("accountId").asText());
        assertEquals("1234-****-****-5678", result.getDisplayCard().path("items").get(0)
                .path("accountNumberMasked").asText());
        assertEquals(48200L, result.getDisplayCard().path("items").get(0).path("balance").asLong());
        assertEquals(1, result.getSlots().get("accountCount"));
        verify(accountService).getAccounts(UUID.fromString(USER_ID));
    }

    @Test
    void listsMultipleAccountsWithoutSelectingOneAsTheDefault() {
        when(accountService.getAccounts(UUID.fromString(USER_ID))).thenReturn(List.of(
                account("생활비 통장", 48200L), account("저축 통장", 150000L)));

        VoiceTurnAnalysisResult result = resolver.resolve(USER_ID, accountInquiry());

        assertEquals("사용 중인 계좌는 2개입니다. 계좌 목록을 화면에 보여드릴게요.", result.getTtsText());
        assertEquals(2, result.getDisplayCard().path("totalCount").asInt());
        assertEquals(2, result.getDisplayCard().path("items").size());
    }

    @Test
    void returnsAnEmptyAccountGuideWhenThereAreNoActiveAccounts() {
        when(accountService.getAccounts(UUID.fromString(USER_ID))).thenReturn(List.of());

        VoiceTurnAnalysisResult result = resolver.resolve(USER_ID, accountInquiry());

        assertEquals("사용 중인 계좌가 없습니다. 계좌를 등록한 뒤 다시 확인해 주세요.", result.getTtsText());
        assertEquals("ACCOUNT_LIST", result.getDisplayCard().path("type").asText());
        assertEquals(0, result.getDisplayCard().path("totalCount").asInt());
        assertEquals(0, result.getDisplayCard().path("items").size());
    }

    @Test
    void returnsARetryGuideWhenTheAccountQueryFails() {
        when(accountService.getAccounts(UUID.fromString(USER_ID)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        VoiceTurnAnalysisResult result = resolver.resolve(USER_ID, accountInquiry());

        assertEquals("계좌 정보를 불러오지 못했어요. 잠시 후 다시 말씀해 주세요.", result.getTtsText());
        assertEquals("ACCOUNT_QUERY_FAILED", result.getDisplayCard().path("type").asText());
        assertEmptyActions(result.getDisplayCard());
        assertEquals(VoiceRequestedFunction.NONE, result.getRequestedFunction());
        assertEquals(VoiceNextAction.REASK_INPUT, result.getNextAction());
        assertEquals(DialogueStep.AWAITING_INPUT, result.getNextStep());
    }

    @Test
    void doesNotQueryAccountsForAnotherRequestedFunction() {
        VoiceTurnAnalysisResult billInquiry = new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.FINANCIAL_TASK,
                Map.of(),
                BigDecimal.ONE,
                "",
                "",
                null,
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.BILL_INQUIRY);

        assertSame(billInquiry, resolver.resolve(USER_ID, billInquiry));
    }

    private AccountResponse account(String accountName, long balance) {
        return new AccountResponse(
                UUID.randomUUID(),
                "004",
                "1234-****-****-5678",
                accountName,
                balance,
                "SAVINGS",
                true,
                OffsetDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    private VoiceTurnAnalysisResult accountInquiry() {
        return new VoiceTurnAnalysisResult(
                DialogueStep.AWAITING_INPUT,
                VoiceIntent.ACCOUNT_INQUIRY,
                Map.of("query", "balance"),
                BigDecimal.ONE,
                "계좌를 확인해 드릴게요.",
                "<speak>계좌를 확인해 드릴게요.</speak>",
                null,
                null,
                null,
                VoiceNextAction.PRESENT_RESULT,
                VoiceRequestedFunction.ACCOUNT_INQUIRY);
    }

    private void assertEmptyActions(com.fasterxml.jackson.databind.JsonNode displayCard) {
        assertTrue(displayCard.path("actions").isArray());
        assertEquals(0, displayCard.path("actions").size());
    }
}
