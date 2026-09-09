package com.silvertown.domain.voice.adaptation;

import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceAdaptationSignal;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Holds only short-lived, per-session adaptation state; no transcript or financial values are retained. */
@Component
public class VoiceAdaptationSessionStateStore {
    private static final BigDecimal DEFAULT_SPEECH_RATE = new BigDecimal("1.05");

    private final VoiceAdaptationPolicy policy;
    private final ConcurrentHashMap<String, SessionState> states = new ConcurrentHashMap<>();

    public VoiceAdaptationSessionStateStore(VoiceAdaptationPolicy policy) {
        this.policy = policy;
    }

    public void recordSignals(
            String sessionId, DialogueStep currentDecisionStep, Set<VoiceAdaptationSignal> signals) {
        if (signals.isEmpty()) {
            return;
        }
        states.compute(sessionId, (key, current) -> transition(current, currentDecisionStep, signals,
                current == null ? 0 : current.reaskCount(), false));
    }

    public void recordReask(String sessionId, DialogueStep currentDecisionStep) {
        states.compute(sessionId, (key, current) -> {
            int nextReaskCount = current != null && current.reaskStep() == currentDecisionStep
                    ? current.reaskCount() + 1 : 1;
            Set<VoiceAdaptationSignal> signals = nextReaskCount >= 2
                    ? Set.of(VoiceAdaptationSignal.REPEATED_REASK) : Set.of();
            return transition(current, currentDecisionStep, signals, nextReaskCount, true);
        });
    }

    public void recordNormalAdvance(String sessionId, DialogueStep currentDecisionStep) {
        recordSignals(sessionId, currentDecisionStep, Set.of(VoiceAdaptationSignal.NORMAL_ADVANCE));
    }

    public void responseRendered(String sessionId, DialogueStep currentDecisionStep) {
        recordSignals(sessionId, currentDecisionStep, Set.of(VoiceAdaptationSignal.RESPONSE_RENDERED));
    }

    public void clear(String sessionId) {
        states.remove(sessionId);
    }

    public void initialize(String sessionId, DialogueStep currentDecisionStep, VoiceGuidanceMode initialMode) {
        if (initialMode == VoiceGuidanceMode.STANDARD) {
            return;
        }
        states.putIfAbsent(sessionId, new SessionState(
                new VoiceAdaptationState(initialMode, GuidanceScope.CURRENT_SESSION, 0,
                        currentDecisionStep, 0),
                currentDecisionStep,
                0));
    }

    /** Returns the current short-lived state for the response renderer. */
    public VoiceAdaptationState stateOf(String sessionId) {
        SessionState state = states.get(sessionId);
        return state == null ? null : state.policyState();
    }

    private SessionState transition(
            SessionState current,
            DialogueStep currentDecisionStep,
            Set<VoiceAdaptationSignal> signals,
            int reaskCount,
            boolean preserveNewReaskCount) {
        VoiceAdaptationState previous = current == null
                ? VoiceAdaptationState.initial(currentDecisionStep) : current.policyState();
        VoiceAdaptationDecision decision = policy.decide(
                previous, currentDecisionStep, signals, DEFAULT_SPEECH_RATE);
        boolean decisionStepChanged = current != null && current.reaskStep() != currentDecisionStep;
        return new SessionState(
                decision.nextState(),
                currentDecisionStep,
                decisionStepChanged && !preserveNewReaskCount ? 0 : reaskCount);
    }

    private record SessionState(
            VoiceAdaptationState policyState, DialogueStep reaskStep, int reaskCount) {}
}
