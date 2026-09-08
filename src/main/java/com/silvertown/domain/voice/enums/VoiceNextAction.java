package com.silvertown.domain.voice.enums;

/** User-facing or internal next action paired with one dialogue step. */
public enum VoiceNextAction {
    REASK_INPUT(DialogueStep.AWAITING_INPUT),
    RECONFIRM_INPUT(DialogueStep.RECONFIRMING),
    ASK_RECIPIENT(DialogueStep.AWAITING_RECIPIENT),
    ASK_AMOUNT(DialogueStep.AWAITING_AMOUNT),
    ASK_CONTINUATION(DialogueStep.AWAITING_CONTINUATION),
    ASK_FINAL_APPROVAL(DialogueStep.WAITING_FINAL_APPROVAL),
    ASK_PIN(DialogueStep.WAITING_FINAL_APPROVAL),
    WAIT_GUARDIAN_VERIFICATION(DialogueStep.HELD),
    PRESENT_RESULT(DialogueStep.AWAITING_INPUT),
    NONE(DialogueStep.RISK_CHECK),
    END_SESSION(DialogueStep.CANCELLED);

    private final DialogueStep dialogueStep;

    VoiceNextAction(DialogueStep dialogueStep) {
        this.dialogueStep = dialogueStep;
    }

    public boolean supports(DialogueStep candidateStep) {
        return dialogueStep == candidateStep;
    }

    public DialogueStep getDialogueStep() {
        return dialogueStep;
    }
}
