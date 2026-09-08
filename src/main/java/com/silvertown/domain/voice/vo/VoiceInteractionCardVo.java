package com.silvertown.domain.voice.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VoiceInteractionCardVo {
    private String sessionId;
    private String cardId;
    private int cardVersion;
    private String sourceTurnId;
    private String cardType;
    private String actions;
    private String candidateItems;
    private String focusedItemId;
    private String confirmedRecipientId;
    private Long confirmedAmount;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
