package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceStreamTicketResponse;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.VoiceStreamTicketMapper;
import com.silvertown.domain.voice.service.impl.VoiceStreamTicketServiceImpl;
import com.silvertown.domain.voice.vo.VoiceStreamTicketVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class VoiceStreamTicketServiceImplTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-09T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    private VoiceSessionService voiceSessionService;
    private VoiceStreamTicketMapper voiceStreamTicketMapper;
    private VoiceStreamTicketService service;

    @BeforeEach
    void setUp() {
        voiceSessionService = Mockito.mock(VoiceSessionService.class);
        voiceStreamTicketMapper = Mockito.mock(VoiceStreamTicketMapper.class);
        service = new VoiceStreamTicketServiceImpl(voiceSessionService, voiceStreamTicketMapper, CLOCK);
    }

    @Test
    void issuesOpaqueTicketWithOnlySha256HashPersistedForEligibleStreamSession() {
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(eligibleSession());

        VoiceStreamTicketResponse response = service.issue(USER_ID, SESSION_ID);

        ArgumentCaptor<VoiceStreamTicketVo> ticketCaptor =
                ArgumentCaptor.forClass(VoiceStreamTicketVo.class);
        verify(voiceStreamTicketMapper).insert(ticketCaptor.capture());
        VoiceStreamTicketVo saved = ticketCaptor.getValue();
        assertTrue(response.getTicket().startsWith("vst_"));
        assertEquals(64, saved.getTicketHash().length());
        assertEquals(sha256(response.getTicket()), saved.getTicketHash());
        assertNotEquals(response.getTicket(), saved.getTicketHash());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(SESSION_ID, saved.getSessionId());
        assertEquals(LocalDateTime.of(2026, 9, 9, 18, 1), saved.getExpiresAt());
        assertEquals(LocalDateTime.of(2026, 9, 9, 18, 0), saved.getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 9, 9, 18, 1).atZone(CLOCK.getZone()).toOffsetDateTime(),
                response.getExpiresAt());
    }

    @Test
    void rejectsTicketIssuanceForNonStreamTransferSession() {
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(new VoiceSessionDetailResponse(
                SESSION_ID, VoiceSessionEntryPoint.GENERAL_FINANCE, VoiceSessionStatus.LISTENING,
                DialogueStep.AWAITING_INPUT, VoiceFlowType.GENERAL_FINANCE, SttMode.CLIENT,
                null, null, null, null));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.issue(USER_ID, SESSION_ID));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(voiceStreamTicketMapper, never()).insert(any());
    }

    @Test
    void rejectsTicketIssuanceForTerminalSession() {
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenReturn(eligibleSession(VoiceSessionStatus.EXPIRED));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.issue(USER_ID, SESSION_ID));

        assertEquals(ErrorCode.VOICE_TURN_CONFLICT, exception.getErrorCode());
        verify(voiceStreamTicketMapper, never()).insert(any());
    }

    @Test
    void doesNotIssueTicketWhenTheRequestedSessionIsNotOwnedByTheAuthenticatedUser() {
        when(voiceSessionService.get(USER_ID, SESSION_ID)).thenThrow(
                new BusinessException(ErrorCode.VOICE_SESSION_NOT_FOUND));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.issue(USER_ID, SESSION_ID));

        assertEquals(ErrorCode.VOICE_SESSION_NOT_FOUND, exception.getErrorCode());
        verify(voiceStreamTicketMapper, never()).insert(any());
    }

    @Test
    void consumesMatchingUnusedUnexpiredTicketOnceAndReturnsItsPrincipalUserId() {
        String opaqueTicket = "vst_opaque-ticket";
        VoiceStreamTicketVo ticket = storedTicket();
        when(voiceStreamTicketMapper.findUnusedUnexpiredByHash(eq(sha256(opaqueTicket)), any()))
                .thenReturn(ticket);
        when(voiceStreamTicketMapper.consumeIfUnusedAndUnexpired(
                eq(ticket.getTicketId()), any(), any())).thenReturn(1);

        Optional<String> consumedUserId = service.consumeForHandshake(SESSION_ID, opaqueTicket);

        assertEquals(Optional.of(USER_ID), consumedUserId);
        verify(voiceStreamTicketMapper).consumeIfUnusedAndUnexpired(
                eq(ticket.getTicketId()), any(), any());
    }

    @Test
    void doesNotConsumeTicketBoundToAnotherSession() {
        String opaqueTicket = "vst_opaque-ticket";
        VoiceStreamTicketVo ticket = storedTicket();
        ticket.setSessionId("20000000-0000-0000-0000-000000000001");
        when(voiceStreamTicketMapper.findUnusedUnexpiredByHash(eq(sha256(opaqueTicket)), any()))
                .thenReturn(ticket);

        assertFalse(service.consumeForHandshake(SESSION_ID, opaqueTicket).isPresent());

        verify(voiceStreamTicketMapper, never()).consumeIfUnusedAndUnexpired(any(), any(), any());
    }

    @Test
    void rejectsAlreadyConsumedOrExpiredTicketWhenConditionalConsumeDoesNotUpdate() {
        String opaqueTicket = "vst_opaque-ticket";
        VoiceStreamTicketVo ticket = storedTicket();
        when(voiceStreamTicketMapper.findUnusedUnexpiredByHash(eq(sha256(opaqueTicket)), any()))
                .thenReturn(ticket);
        when(voiceStreamTicketMapper.consumeIfUnusedAndUnexpired(
                eq(ticket.getTicketId()), any(), any())).thenReturn(0);

        assertFalse(service.consumeForHandshake(SESSION_ID, opaqueTicket).isPresent());
    }

    private VoiceSessionDetailResponse eligibleSession() {
        return eligibleSession(VoiceSessionStatus.LISTENING);
    }

    private VoiceSessionDetailResponse eligibleSession(VoiceSessionStatus status) {
        return new VoiceSessionDetailResponse(
                SESSION_ID, VoiceSessionEntryPoint.TRANSFER, status, DialogueStep.AWAITING_INPUT,
                VoiceFlowType.TRANSFER, SttMode.BACKEND_STREAM,
                null, null, null, null);
    }

    private VoiceStreamTicketVo storedTicket() {
        VoiceStreamTicketVo ticket = new VoiceStreamTicketVo();
        ticket.setTicketId(UUID.randomUUID().toString());
        ticket.setUserId(USER_ID);
        ticket.setSessionId(SESSION_ID);
        return ticket;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
