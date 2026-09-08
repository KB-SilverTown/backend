package com.silvertown.domain.voice.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VoiceUiActionVo {
    private String actionId;
    private String sessionId;
    private String sourceTurnId;
    private String cardId;
    private int cardVersion;
    private String actionType;
    private String itemId;
    private String requestHash;
    private String responseTurnId;
    private LocalDateTime createdAt;
}
