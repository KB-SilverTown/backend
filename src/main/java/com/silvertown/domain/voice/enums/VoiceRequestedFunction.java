package com.silvertown.domain.voice.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Whitelisted internal-function candidates. These values never execute a domain operation directly. */
public enum VoiceRequestedFunction {
    NONE(allowedTransitions(
            transition(VoiceIntent.UNKNOWN, VoiceNextAction.REASK_INPUT, VoiceNextAction.RECONFIRM_INPUT),
            transition(
                    VoiceIntent.TRANSFER,
                    VoiceNextAction.REASK_INPUT,
                    VoiceNextAction.RECONFIRM_INPUT,
                    VoiceNextAction.ASK_RECIPIENT,
                    VoiceNextAction.ASK_AMOUNT),
            transition(VoiceIntent.ACCOUNT_INQUIRY, VoiceNextAction.REASK_INPUT, VoiceNextAction.RECONFIRM_INPUT),
            transition(VoiceIntent.FINANCIAL_TASK, VoiceNextAction.REASK_INPUT, VoiceNextAction.RECONFIRM_INPUT),
            transition(VoiceIntent.MOBILE_BRANCH, VoiceNextAction.REASK_INPUT, VoiceNextAction.RECONFIRM_INPUT))),
    ACCOUNT_INQUIRY(allowedTransitions(
            transition(VoiceIntent.ACCOUNT_INQUIRY, VoiceNextAction.PRESENT_RESULT))),
    TRANSFER_RECIPIENT_CANDIDATES(allowedTransitions(
            transition(VoiceIntent.TRANSFER, VoiceNextAction.ASK_AMOUNT, VoiceNextAction.RECONFIRM_INPUT))),
    TRANSFER_AMOUNT_VALIDATION(allowedTransitions(
            transition(VoiceIntent.TRANSFER, VoiceNextAction.ASK_RECIPIENT, VoiceNextAction.RECONFIRM_INPUT))),
    TRANSFER_DRAFT_PREPARE(allowedTransitions(
            transition(VoiceIntent.TRANSFER, VoiceNextAction.NONE))),
    TRANSFER_RISK_ASSESS(allowedTransitions(
            transition(VoiceIntent.TRANSFER, VoiceNextAction.NONE))),
    TRANSFER_RISK_CHECK(allowedTransitions(
            transition(VoiceIntent.TRANSFER, VoiceNextAction.NONE))),
    FINANCIAL_TASK_CLASSIFY(allowedTransitions(
            transition(VoiceIntent.FINANCIAL_TASK, VoiceNextAction.PRESENT_RESULT))),
    BILL_INQUIRY(allowedTransitions(
            transition(VoiceIntent.FINANCIAL_TASK, VoiceNextAction.PRESENT_RESULT))),
    BILL_PAYMENT(allowedTransitions(
            transition(VoiceIntent.FINANCIAL_TASK, VoiceNextAction.PRESENT_RESULT))),
    MOBILE_BRANCH_RECOMMEND(allowedTransitions(
            transition(VoiceIntent.MOBILE_BRANCH, VoiceNextAction.PRESENT_RESULT)));

    private final Map<VoiceIntent, Set<VoiceNextAction>> allowedTransitions;

    VoiceRequestedFunction(Map<VoiceIntent, Set<VoiceNextAction>> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    public boolean supports(VoiceIntent intent) {
        return allowedTransitions.containsKey(intent);
    }

    public boolean supports(VoiceIntent intent, VoiceNextAction nextAction) {
        Set<VoiceNextAction> nextActions = allowedTransitions.get(intent);
        return nextActions != null && nextActions.contains(nextAction);
    }

    private static Map<VoiceIntent, Set<VoiceNextAction>> allowedTransitions(AllowedTransition... transitions) {
        Map<VoiceIntent, Set<VoiceNextAction>> result = new EnumMap<>(VoiceIntent.class);
        for (AllowedTransition transition : transitions) {
            result.put(transition.intent, transition.nextActions);
        }
        return result;
    }

    private static AllowedTransition transition(VoiceIntent intent, VoiceNextAction... nextActions) {
        Set<VoiceNextAction> allowedNextActions = EnumSet.noneOf(VoiceNextAction.class);
        for (VoiceNextAction nextAction : nextActions) {
            allowedNextActions.add(nextAction);
        }
        return new AllowedTransition(intent, allowedNextActions);
    }

    private static class AllowedTransition {
        private final VoiceIntent intent;
        private final Set<VoiceNextAction> nextActions;

        private AllowedTransition(VoiceIntent intent, Set<VoiceNextAction> nextActions) {
            this.intent = intent;
            this.nextActions = nextActions;
        }
    }
}
