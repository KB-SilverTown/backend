package com.silvertown.domain.voice.dto;

import java.math.BigDecimal;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceSettingsRequest {
    @Pattern(
            regexp = "ko-KR-(JiMin|GookMin)Neural",
            message = "지원하지 않는 음성입니다."
    )
    private String ttsVoice;

    @DecimalMin(value = "0.90", message = "말하기 속도는 0.90 이상이어야 합니다.")
    @DecimalMax(value = "1.20", message = "말하기 속도는 1.20 이하여야 합니다.")
    private BigDecimal speechRateMultiplier;

    @DecimalMin(value = "1.00", message = "음량은 1.00 이상이어야 합니다.")
    @DecimalMax(value = "1.20", message = "음량은 1.20 이하여야 합니다.")
    private BigDecimal volumeMultiplier;

    @AssertTrue(message = "음성, 말하기 속도, 음량 중 하나 이상은 필요합니다.")
    public boolean isAnySettingProvided() {
        return ttsVoice != null || speechRateMultiplier != null || volumeMultiplier != null;
    }
}
