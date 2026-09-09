package com.silvertown.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.silvertown.domain.voice.adaptation.VoiceAdaptationState;
import com.silvertown.domain.voice.adaptation.VoiceGuidanceCommand;
import com.silvertown.domain.voice.enums.DialogueStep;
import com.silvertown.domain.voice.enums.GuidanceScope;
import com.silvertown.domain.voice.enums.VoiceGuidanceMode;
import com.silvertown.domain.voice.mapper.UserVoiceSettingsMapper;
import com.silvertown.domain.voice.vo.UserVoiceSettingsVo;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class VoiceGuidanceSettingsServiceTest {
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void persistsExplicitSpeedAndVolumeChangesWithinBounds() {
        UserVoiceSettingsMapper mapper = Mockito.mock(UserVoiceSettingsMapper.class);
        UserVoiceSettingsVo stored = settings("1.20", "1.00");
        when(mapper.findByUserId(USER_ID)).thenReturn(stored);
        VoiceGuidanceSettingsService service = new VoiceGuidanceSettingsService(mapper);

        service.applyExplicitCommand(USER_ID, VoiceGuidanceCommand.FASTER);
        service.applyExplicitCommand(USER_ID, VoiceGuidanceCommand.LOUDER);

        ArgumentCaptor<UserVoiceSettingsVo> captor = ArgumentCaptor.forClass(UserVoiceSettingsVo.class);
        verify(mapper, Mockito.times(2)).upsert(captor.capture());
        assertEquals(new BigDecimal("1.20"), captor.getAllValues().get(0).getSpeechRateMultiplier());
        assertEquals(VoiceGuidanceMode.COMPACT.name(), captor.getAllValues().get(0).getPreferredVerbosity());
        assertEquals(new BigDecimal("1.05"), captor.getAllValues().get(1).getVolumeMultiplier());
    }

    @Test
    void reportsWhenLouderRequestReachesMaximumVolume() {
        UserVoiceSettingsMapper mapper = Mockito.mock(UserVoiceSettingsMapper.class);
        when(mapper.findByUserId(USER_ID)).thenReturn(settings("1.05", "1.20"));

        assertTrue(new VoiceGuidanceSettingsService(mapper)
                .applyExplicitCommand(USER_ID, VoiceGuidanceCommand.LOUDER));
    }

    @Test
    void storesSupportStartFlagAfterTwoBehaviorSignals() {
        UserVoiceSettingsMapper mapper = Mockito.mock(UserVoiceSettingsMapper.class);
        when(mapper.findByUserId(USER_ID)).thenReturn(settings("1.05", "1.00"));
        VoiceGuidanceSettingsService service = new VoiceGuidanceSettingsService(mapper);

        service.completeSession(USER_ID, new VoiceAdaptationState(
                VoiceGuidanceMode.SUPPORT, GuidanceScope.CURRENT_STEP, 2,
                DialogueStep.AWAITING_AMOUNT, 0));

        ArgumentCaptor<UserVoiceSettingsVo> captor = ArgumentCaptor.forClass(UserVoiceSettingsVo.class);
        verify(mapper).upsert(captor.capture());
        assertEquals(true, captor.getValue().getSupportStartNextSession());
        assertEquals(2, captor.getValue().getRecentSupportSignalCount());
    }

    private UserVoiceSettingsVo settings(String rate, String volume) {
        UserVoiceSettingsVo value = new UserVoiceSettingsVo();
        value.setUserId(USER_ID);
        value.setSpeechRateMultiplier(new BigDecimal(rate));
        value.setVolumeMultiplier(new BigDecimal(volume));
        value.setPreferredVerbosity(VoiceGuidanceMode.STANDARD.name());
        value.setSupportStartNextSession(false);
        value.setRecentSupportSignalCount(0);
        return value;
    }
}
