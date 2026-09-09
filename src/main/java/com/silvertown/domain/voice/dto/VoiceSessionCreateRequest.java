package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceSessionCreateRequest {
    @NotNull(message = "음성 세션 진입점은 필수입니다.")
    private VoiceSessionEntryPoint entryPoint;
}
