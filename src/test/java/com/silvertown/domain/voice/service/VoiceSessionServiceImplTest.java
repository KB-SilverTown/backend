package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvertown.domain.voice.dto.VoiceSessionCreateRequest;
import com.silvertown.domain.account.mapper.AccountMapper;
import com.silvertown.domain.account.vo.BankAccount;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceSessionResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.DialogueTurnMapper;
import com.silvertown.domain.voice.mapper.VoiceInteractionCardMapper;
import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.service.impl.VoiceSessionServiceImpl;
import com.silvertown.domain.voice.vo.DialogueTurnVo;
import com.silvertown.domain.voice.vo.VoiceSessionVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class VoiceSessionServiceImplTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VoiceSessionMapper voiceSessionMapper;
    private AccountMapper accountMapper;
    private VoiceTransferOrchestrator voiceTransferOrchestrator;
    private VoiceInteractionCardMapper voiceInteractionCardMapper;
    private DialogueTurnMapper dialogueTurnMapper;
    private UserVoiceSettingsMapper userVoiceSettingsMapper;
    private VoiceSessionService service;

    @BeforeEach
    void setUp() {
        voiceSessionMapper = Mockito.mock(VoiceSessionMapper.class);
        accountMapper = Mockito.mock(AccountMapper.class);
        voiceTransferOrchestrator = Mockito.mock(VoiceTransferOrchestrator.class);
        voiceInteractionCardMapper = Mockito.mock(VoiceInteractionCardMapper.class);
        dialogueTurnMapper = Mockito.mock(DialogueTurnMapper.class);
        userVoiceSettingsMapper = Mockito.mock(UserVoiceSettingsMapper.class);
        service = new VoiceSessionServiceImpl(
                voiceSessionMapper,
                accountMapper,
                voiceTransferOrchestrator,
                voiceInteractionCardMapper,
                dialogueTurnMapper,
                new VoiceSessionPromptProvider(),
                new VoiceSsmlRenderer(userVoiceSettingsMapper),
                objectMapper,
                CLOCK);
    }

    @Test
    void createsGeneralFinanceSessionWithDerivedModeAndFirstPrompt() throws Exception {
        when(dialogueTurnMapper.findNextSequenceNo(Mockito.anyString())).thenReturn(1);

        VoiceSessionResponse response = service.create(USER_ID.toString(),
                request("GENERAL_FINANCE"));

        ArgumentCaptor<VoiceSessionVo> captor = ArgumentCaptor.forClass(VoiceSessionVo.class);
        ArgumentCaptor<DialogueTurnVo> dialogueTurnCaptor = ArgumentCaptor.forClass(DialogueTurnVo.class);
        verify(voiceSessionMapper).insert(captor.capture());
        verify(dialogueTurnMapper).insert(dialogueTurnCaptor.capture());
        VoiceSessionVo saved = captor.getValue();
        DialogueTurnVo firstTurn = dialogueTurnCaptor.getValue();
        assertEquals(USER_ID.toString(), saved.getUserId());
        assertEquals(VoiceSessionStatus.LISTENING.name(), saved.getStatus());
        assertEquals(DialogueStep.AWAITING_INPUT.name(), saved.getCurrentStep());
        assertEquals("GENERAL_FINANCE", saved.getFlowType());
        assertEquals("CLIENT", saved.getSttMode());
        assertEquals("GENERAL_FINANCE", saved.getEntryPoint());
        assertEquals(VoiceSessionEntryPoint.GENERAL_FINANCE, response.getEntryPoint());
        assertNull(response.getNavigation());
        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 15), saved.getExpiresAt());
        assertEquals("안녕하세요. 잔액, 거래 내역, 금융 일정 중 필요한 내용을 말씀해 주세요.",
                response.getFirstPrompt());
        assertEquals(saved.getSessionId(), firstTurn.getSessionId());
        assertEquals(1, firstTurn.getSequenceNo());
        assertEquals("AI", firstTurn.getSpeaker());
        assertEquals(response.getFirstPrompt(), firstTurn.getTtsText());
        assertEquals(DialogueStep.AWAITING_INPUT.name(), firstTurn.getStep());
        assertEquals("<speak version=\"1.0\" xml:lang=\"ko-KR\" xmlns=\"http://www.w3.org/2001/10/synthesis\">"
                        + "<voice name=\"ko-KR-JiMinNeural\"><prosody rate=\"1.05\" pitch=\"-3%\" volume=\"100\">"
                        + response.getFirstPrompt() + "</prosody></voice></speak>",
                firstTurn.getTtsSsml());
        assertNull(firstTurn.getDisplayCard());
    }

    @Test
    void createsBillPaymentSessionWithCameraNavigation() throws Exception {
        when(dialogueTurnMapper.findNextSequenceNo(Mockito.anyString())).thenReturn(1);

        VoiceSessionResponse response = service.create(USER_ID.toString(), request("BILL_PAYMENT"));

        ArgumentCaptor<VoiceSessionVo> captor = ArgumentCaptor.forClass(VoiceSessionVo.class);
        verify(voiceSessionMapper).insert(captor.capture());
        VoiceSessionVo saved = captor.getValue();
        assertEquals("BILL_PAYMENT", saved.getEntryPoint());
        assertEquals("GENERAL_FINANCE", saved.getFlowType());
        assertEquals("CLIENT", saved.getSttMode());
        assertEquals(VoiceSessionEntryPoint.BILL_PAYMENT, response.getEntryPoint());
        assertEquals("BILL_CAMERA", response.getNavigation().getScreenCode().name());
        assertEquals(saved.getSessionId(), response.getNavigation().getVoiceSessionId());
        assertEquals("고지서를 화면 안에 맞춰 촬영해주세요.", response.getFirstPrompt());
    }

    @Test
    void rejectsRequestWithoutEntryPoint() throws Exception {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(USER_ID.toString(), request(null)));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    void createsTransferSessionWithTheOnlyActiveAccountResolvedByTheServer() throws Exception {
        BankAccount account = new BankAccount();
        account.setAccountId("60000000-0000-0000-0000-000000000001");
        when(accountMapper.findActiveByUserId(USER_ID.toString())).thenReturn(List.of(account));
        when(dialogueTurnMapper.findNextSequenceNo(Mockito.anyString())).thenReturn(1);

        service.create(USER_ID.toString(), request("TRANSFER"));

        ArgumentCaptor<VoiceSessionVo> captor = ArgumentCaptor.forClass(VoiceSessionVo.class);
        verify(voiceSessionMapper).insert(captor.capture());
        assertEquals(account.getAccountId(), captor.getValue().getFromAccountId());
        assertEquals("TRANSFER", captor.getValue().getEntryPoint());
        verify(accountMapper).findActiveByUserId(USER_ID.toString());
    }

    @Test
    void blocksTransferSessionWhenThereIsNoActiveAccount() throws Exception {
        when(accountMapper.findActiveByUserId(USER_ID.toString())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(USER_ID.toString(), request("TRANSFER")));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
        verify(voiceSessionMapper, Mockito.never()).insert(Mockito.any());
    }

    @Test
    void blocksTransferSessionWhenTheActiveAccountIsNotUnique() throws Exception {
        BankAccount first = new BankAccount();
        first.setAccountId("60000000-0000-0000-0000-000000000001");
        BankAccount second = new BankAccount();
        second.setAccountId("60000000-0000-0000-0000-000000000002");
        when(accountMapper.findActiveByUserId(USER_ID.toString())).thenReturn(List.of(first, second));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(USER_ID.toString(), request("TRANSFER")));

        assertEquals(ErrorCode.TRANSFER_INVALID_STATE, exception.getErrorCode());
        verify(voiceSessionMapper, Mockito.never()).insert(Mockito.any());
    }

    @Test
    void getsOwnedSessionWithLatestReplayPayload() {
        VoiceSessionVo voiceSession = voiceSession(VoiceSessionStatus.LISTENING, null);
        voiceSession.setActiveAiTurnId("30000000-0000-0000-0000-000000000001");
        DialogueTurnVo dialogueTurn = new DialogueTurnVo();
        dialogueTurn.setTtsText("다시 안내해 드릴게요.");
        dialogueTurn.setTtsSsml("<speak>다시 안내해 드릴게요.</speak>");
        dialogueTurn.setDisplayCard("{\"type\":\"balance\"}");
        when(voiceSessionMapper.findOwnedById(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(voiceSession);
        when(dialogueTurnMapper.findLatestReplayableAiTurn(SESSION_ID.toString()))
                .thenReturn(dialogueTurn);

        VoiceSessionDetailResponse response = service.get(USER_ID.toString(), SESSION_ID.toString());

        assertEquals(SESSION_ID.toString(), response.getSessionId());
        assertEquals(VoiceSessionStatus.LISTENING, response.getStatus());
        assertEquals("30000000-0000-0000-0000-000000000001", response.getActiveAiTurnId());
        assertEquals("다시 안내해 드릴게요.", response.getLatestReplayPayload().getTtsText());
        assertEquals("balance", response.getLatestReplayPayload().getDisplayCard().get("type").asText());
    }

    @Test
    void getsBillPaymentSessionWithCameraNavigation() {
        VoiceSessionVo voiceSession = voiceSession(VoiceSessionStatus.LISTENING, null);
        voiceSession.setEntryPoint(VoiceSessionEntryPoint.BILL_PAYMENT.name());
        when(voiceSessionMapper.findOwnedById(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(voiceSession);
        when(dialogueTurnMapper.findLatestReplayableAiTurn(SESSION_ID.toString())).thenReturn(null);

        VoiceSessionDetailResponse response = service.get(USER_ID.toString(), SESSION_ID.toString());

        assertEquals(VoiceSessionEntryPoint.BILL_PAYMENT, response.getEntryPoint());
        assertEquals("BILL_CAMERA", response.getNavigation().getScreenCode().name());
        assertEquals(SESSION_ID.toString(), response.getNavigation().getVoiceSessionId());
    }

    @Test
    void expiresSessionWhenCurrentTimeEqualsExpiresAt() {
        VoiceSessionVo active = voiceSession(VoiceSessionStatus.LISTENING, null);
        active.setTransferId("30000000-0000-0000-0000-000000000001");
        active.setExpiresAt(LocalDateTime.of(2026, 9, 2, 10, 0));
        VoiceSessionVo expired = voiceSession(VoiceSessionStatus.EXPIRED, null);
        expired.setExpiresAt(LocalDateTime.of(2026, 9, 2, 10, 0));
        when(voiceSessionMapper.findOwnedById(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(active, expired);

        VoiceSessionDetailResponse response = service.get(USER_ID.toString(), SESSION_ID.toString());

        verify(voiceSessionMapper).updateStatusAndStep(
                USER_ID.toString(), SESSION_ID.toString(), VoiceSessionStatus.EXPIRED.name(),
                DialogueStep.AWAITING_INPUT.name());
        verify(voiceInteractionCardMapper).deactivateActiveBySessionId(SESSION_ID.toString());
        verify(voiceTransferOrchestrator).cancelUnexecuted(
                USER_ID, UUID.fromString(active.getTransferId()));
        assertEquals(VoiceSessionStatus.EXPIRED, response.getStatus());
    }

    @Test
    void closesActiveSessionAndReturnsCurrentState() {
        VoiceSessionVo active = voiceSession(VoiceSessionStatus.LISTENING, null);
        active.setTransferId("30000000-0000-0000-0000-000000000001");
        VoiceSessionVo closed = voiceSession(VoiceSessionStatus.CLOSED,
                LocalDateTime.of(2026, 9, 2, 10, 1));
        when(voiceSessionMapper.findOwnedById(USER_ID.toString(), SESSION_ID.toString()))
                .thenReturn(active, closed);
        when(dialogueTurnMapper.findLatestReplayableAiTurn(SESSION_ID.toString())).thenReturn(null);

        VoiceSessionDetailResponse response = service.close(USER_ID.toString(), SESSION_ID.toString());

        verify(voiceSessionMapper).closeOwned(
                eq(USER_ID.toString()), eq(SESSION_ID.toString()),
                eq(DialogueStep.AWAITING_INPUT.name()), any(LocalDateTime.class));
        verify(voiceInteractionCardMapper).deactivateActiveBySessionId(SESSION_ID.toString());
        verify(voiceTransferOrchestrator).cancelUnexecuted(
                USER_ID, UUID.fromString(active.getTransferId()));
        assertEquals(VoiceSessionStatus.CLOSED, response.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 2, 10, 1).atZone(CLOCK.getZone()).toOffsetDateTime(),
                response.getEndedAt());
        assertNull(response.getLatestReplayPayload());
    }

    private VoiceSessionCreateRequest request(String entryPoint) throws Exception {
        return objectMapper.readValue(
                entryPoint == null ? "{}" : "{\"entryPoint\":\"" + entryPoint + "\"}",
                VoiceSessionCreateRequest.class);
    }

    private VoiceSessionVo voiceSession(VoiceSessionStatus status, LocalDateTime endedAt) {
        VoiceSessionVo voiceSession = new VoiceSessionVo();
        voiceSession.setSessionId(SESSION_ID.toString());
        voiceSession.setUserId(USER_ID.toString());
        voiceSession.setStatus(status.name());
        voiceSession.setCurrentStep(DialogueStep.AWAITING_INPUT.name());
        voiceSession.setFlowType("GENERAL_FINANCE");
        voiceSession.setSttMode("CLIENT");
        voiceSession.setEntryPoint("GENERAL_FINANCE");
        voiceSession.setStartedAt(LocalDateTime.of(2026, 9, 2, 10, 0));
        voiceSession.setExpiresAt(LocalDateTime.of(2026, 9, 2, 10, 15));
        voiceSession.setEndedAt(endedAt);
        return voiceSession;
    }
}
