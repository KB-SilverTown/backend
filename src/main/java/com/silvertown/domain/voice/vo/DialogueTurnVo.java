package com.silvertown.domain.voice.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DialogueTurnVo {
    private String turnId;
    private String sessionId;
    private int sequenceNo;
    private String speaker;
    private String transcript;
    private String ttsText;
    private String ttsSsml;
    private String displayCard;
    private String step;
    private String intent;
    private String extractedSlots;
    private int silenceMs;
    private int replayCount;
    private boolean interrupted;
    private BigDecimal sttConfidence;
    private String inputType;
    private LocalDateTime createdAt;
}
