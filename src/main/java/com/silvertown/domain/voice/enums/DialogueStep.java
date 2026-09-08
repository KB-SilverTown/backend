package com.silvertown.domain.voice.enums;

public enum DialogueStep {
    AWAITING_INPUT,
    AWAITING_RECIPIENT,
    AWAITING_AMOUNT,
    AWAITING_CONTINUATION,
    RECONFIRMING,
    RISK_CHECK,
    WAITING_FINAL_APPROVAL,
    HELD,
    COMPLETED,
    CANCELLED
}
