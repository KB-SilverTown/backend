package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceSessionEventRequest {
    @NotBlank(message = "음성 세션 이벤트 유형은 필수입니다.")
    @Pattern(
            regexp = "INTERRUPTED|REPLAY",
            message = "음성 세션 이벤트 유형은 INTERRUPTED 또는 REPLAY여야 합니다.")
    private String eventType;

    @NotNull(message = "대화 턴 식별자는 필수입니다.")
    @Pattern(
            regexp = VoiceIdentifierPattern.CANONICAL_UUID_REGEX,
            message = "대화 턴 식별자는 canonical UUID 형식이어야 합니다.")
    private String turnId;
}
