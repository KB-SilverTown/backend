package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.enums.VoiceUiActionType;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceUiActionRequest {
    @NotNull
    @Pattern(regexp = VoiceIdentifierPattern.CANONICAL_UUID_REGEX)
    private String actionId;

    @NotNull
    @Pattern(regexp = VoiceIdentifierPattern.CANONICAL_UUID_REGEX)
    private String sourceTurnId;

    @NotNull
    @Pattern(regexp = VoiceIdentifierPattern.CANONICAL_UUID_REGEX)
    private String cardId;

    @NotNull
    @Min(1)
    private Integer cardVersion;

    @NotNull
    private VoiceUiActionType actionType;

    @Pattern(regexp = VoiceIdentifierPattern.CANONICAL_UUID_REGEX)
    private String itemId;
}
