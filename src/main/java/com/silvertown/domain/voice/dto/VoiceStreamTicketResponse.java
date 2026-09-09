package com.silvertown.domain.voice.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceStreamTicketResponse {
    private final String ticket;
    private final OffsetDateTime expiresAt;
}
