package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceStreamTicketResponse;
import java.util.Optional;

public interface VoiceStreamTicketService {
    VoiceStreamTicketResponse issue(String userId, String sessionId);

    Optional<String> consumeForHandshake(String sessionId, String opaqueTicket);
}
