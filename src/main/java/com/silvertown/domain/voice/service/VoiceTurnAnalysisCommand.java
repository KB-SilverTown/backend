package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.enums.DialogueInputType;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.VoiceFlowType;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class VoiceTurnAnalysisCommand {
    private final String userId;
    private final String sessionId;
    private final String turnId;
    private final VoiceFlowType flowType;
    private final DialogueStep currentStep;
    private final String transcript;
    private final BigDecimal sttConfidence;
    private final DialogueInputType inputType;
}
