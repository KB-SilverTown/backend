package com.silvertown.domain.voice.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserVoiceSettingsVo {
    private String userId;
    private String voiceName;
    private BigDecimal speechRateMultiplier;
    private BigDecimal volumeMultiplier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
