package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceStreamTicketResponse;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import com.silvertown.domain.voice.mapper.VoiceStreamTicketMapper;
import com.silvertown.domain.voice.service.VoiceSessionService;
import com.silvertown.domain.voice.service.VoiceStreamTicketService;
import com.silvertown.domain.voice.vo.VoiceStreamTicketVo;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceStreamTicketServiceImpl implements VoiceStreamTicketService {
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);
    private static final int TICKET_RANDOM_BYTES = 32;
    private static final String TICKET_PREFIX = "vst_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final VoiceSessionService voiceSessionService;
    private final VoiceStreamTicketMapper voiceStreamTicketMapper;
    private final Clock clock;

    @Override
    @Transactional
    public VoiceStreamTicketResponse issue(String userId, String sessionId) {
        VoiceSessionDetailResponse voiceSession = voiceSessionService.get(userId, sessionId);
        requireEligibleStreamSession(voiceSession);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(TICKET_TTL);
        String opaqueTicket = newOpaqueTicket();

        VoiceStreamTicketVo ticket = new VoiceStreamTicketVo();
        ticket.setTicketId(UUID.randomUUID().toString());
        ticket.setTicketHash(sha256(opaqueTicket));
        ticket.setUserId(userId);
        ticket.setSessionId(sessionId);
        ticket.setExpiresAt(expiresAt);
        ticket.setCreatedAt(now);
        voiceStreamTicketMapper.insert(ticket);

        return new VoiceStreamTicketResponse(opaqueTicket, toOffsetDateTime(expiresAt));
    }

    @Override
    @Transactional
    public Optional<String> consumeForHandshake(String sessionId, String opaqueTicket) {
        if (opaqueTicket == null || opaqueTicket.isBlank()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        VoiceStreamTicketVo ticket = voiceStreamTicketMapper.findUnusedUnexpiredByHash(
                sha256(opaqueTicket), now);
        if (ticket == null || !sessionId.equals(ticket.getSessionId())) {
            return Optional.empty();
        }

        int consumed = voiceStreamTicketMapper.consumeIfUnusedAndUnexpired(
                ticket.getTicketId(), now, now);
        return consumed == 1 ? Optional.of(ticket.getUserId()) : Optional.empty();
    }

    private void requireEligibleStreamSession(VoiceSessionDetailResponse voiceSession) {
        if (voiceSession.getFlowType() != VoiceFlowType.TRANSFER
                || voiceSession.getSttMode() != SttMode.BACKEND_STREAM) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (voiceSession.getStatus() == VoiceSessionStatus.CLOSED
                || voiceSession.getStatus() == VoiceSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }

    private String newOpaqueTicket() {
        byte[] randomBytes = new byte[TICKET_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return TICKET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }
}
