package com.silvertown.domain.voice.dto;

import com.silvertown.domain.voice.enums.DialogueInputType;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceTurnRequest {
    @NotNull(message = "대화 턴 식별자는 필수입니다.")
    @Pattern(
            regexp = VoiceIdentifierPattern.CANONICAL_UUID_REGEX,
            message = "대화 턴 식별자는 canonical UUID 형식이어야 합니다.")
    private String turnId;

    @NotBlank(message = "인식된 문장은 필수입니다.")
    @Size(max = 500, message = "인식된 문장은 500자 이하여야 합니다.")
    private String transcript;

    @NotNull(message = "STT 인식 신뢰도는 필수입니다.")
    @DecimalMin(value = "0.0", message = "STT 인식 신뢰도는 0 이상이어야 합니다.")
    @DecimalMax(value = "1.0", message = "STT 인식 신뢰도는 1 이하여야 합니다.")
    private BigDecimal sttConfidence;

    @NotNull(message = "입력 경로는 필수입니다.")
    private DialogueInputType inputType;

    public static VoiceTurnRequest azureFinal(
            String turnId, String transcript, BigDecimal sttConfidence) {
        VoiceTurnRequest request = new VoiceTurnRequest();
        request.turnId = turnId;
        request.transcript = transcript;
        request.sttConfidence = sttConfidence;
        request.inputType = DialogueInputType.VOICE;
        return request;
    }
}
