package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.enums.DialogueStep;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceSessionEventResponse {
    private final DialogueStep state;
    private final String invalidatedTurnId;
    private final VoiceReplayPayloadResponse replayPayload;
}
