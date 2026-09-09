package com.silvertown.domain.voice.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VoiceSessionVo {

    private String sessionId;
    private String userId;
    private String fromAccountId;
    private String transferId;
    private String status;
    private String currentStep;
    private String flowType;
    private String sttMode;
    private String entryPoint;
    private String activeInputTurnId;
    private String activeAiTurnId;
    private long lifecycleGeneration;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime expiresAt;
}
