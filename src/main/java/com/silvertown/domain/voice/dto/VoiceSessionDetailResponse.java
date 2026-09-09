package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.SttMode;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import com.silvertown.domain.voice.enums.VoiceSessionStatus;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VoiceSessionDetailResponse {
    private final String sessionId;
    private final VoiceSessionEntryPoint entryPoint;
    private final VoiceSessionStatus status;
    private final DialogueStep currentStep;
    private final VoiceFlowType flowType;
    private final SttMode sttMode;
    private final OffsetDateTime expiresAt;
    private final OffsetDateTime endedAt;
    private final VoiceReplayPayloadResponse latestReplayPayload;
    private final VoiceSessionNavigationResponse navigation;
}
