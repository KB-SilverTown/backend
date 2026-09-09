package com.silvertown.domain.voice.adaptation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceAdaptationSignal;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VoiceAdaptationSessionStateStoreTest {
    private static final String SESSION_ID = "10000000-0000-0000-0000-000000000001";
    private static final DialogueStep STEP = DialogueStep.AWAITING_AMOUNT;

    private final VoiceAdaptationSessionStateStore store =
            new VoiceAdaptationSessionStateStore(new VoiceAdaptationPolicy());

    @Test
    void replayIsConsumedOnlyAfterTheFollowingNewResponseIsRendered() {
        store.recordSignals(SESSION_ID, STEP, Set.of(VoiceAdaptationSignal.REPLAY));

        assertEquals(VoiceGuidanceMode.SUPPORT, store.stateOf(SESSION_ID).mode());
        assertEquals(GuidanceScope.NEXT_RESPONSE, store.stateOf(SESSION_ID).scope());

        store.responseRendered(SESSION_ID, STEP);

        assertEquals(VoiceGuidanceMode.STANDARD, store.stateOf(SESSION_ID).mode());
    }

    @Test
    void secondReplayWithinTheSameStepPromotesSupportToCurrentStep() {
        store.recordSignals(SESSION_ID, STEP, Set.of(VoiceAdaptationSignal.REPLAY));
        store.recordSignals(SESSION_ID, STEP, Set.of(VoiceAdaptationSignal.REPLAY));

        assertEquals(VoiceGuidanceMode.SUPPORT, store.stateOf(SESSION_ID).mode());
        assertEquals(GuidanceScope.CURRENT_STEP, store.stateOf(SESSION_ID).scope());
    }

    @Test
    void firstSilenceUsesSupportForTheFollowingPrompt() {
        store.recordSignals(SESSION_ID, STEP, Set.of(VoiceAdaptationSignal.FIRST_SILENCE));

        assertEquals(VoiceGuidanceMode.SUPPORT, store.stateOf(SESSION_ID).mode());
        assertEquals(GuidanceScope.NEXT_RESPONSE, store.stateOf(SESSION_ID).scope());
    }

    @Test
    void repeatedReaskWithinOneStepPromotesSupportToCurrentStep() {
        store.recordReask(SESSION_ID, STEP);
        store.recordReask(SESSION_ID, STEP);

        assertEquals(VoiceGuidanceMode.SUPPORT, store.stateOf(SESSION_ID).mode());
        assertEquals(GuidanceScope.CURRENT_STEP, store.stateOf(SESSION_ID).scope());
    }

    @Test
    void clearRemovesAllSessionState() {
        store.recordSignals(SESSION_ID, STEP, Set.of(VoiceAdaptationSignal.REPLAY));
        store.clear(SESSION_ID);

        assertNull(store.stateOf(SESSION_ID));
    }
}
