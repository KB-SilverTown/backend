package com.silvertown.domain.voice.enums;

public enum VoiceSessionEntryPoint {
    TRANSFER(VoiceFlowType.TRANSFER, SttMode.BACKEND_STREAM),
    GENERAL_FINANCE(VoiceFlowType.GENERAL_FINANCE, SttMode.CLIENT),
    BILL_PAYMENT(VoiceFlowType.GENERAL_FINANCE, SttMode.CLIENT);

    private final VoiceFlowType flowType;
    private final SttMode sttMode;

    VoiceSessionEntryPoint(VoiceFlowType flowType, SttMode sttMode) {
        this.flowType = flowType;
        this.sttMode = sttMode;
    }

    public VoiceFlowType getFlowType() {
        return flowType;
    }

    public SttMode getSttMode() {
        return sttMode;
    }
}
