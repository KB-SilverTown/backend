package com.silvertown.domain.voice.stt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.silvertown.global.common.exception.BusinessException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class AzureSpeechV2StreamingClientTest {
    @Test
    void mapsRecognitionStartTimeoutToSpeechRecognitionFailure() throws Exception {
        @SuppressWarnings("unchecked")
        Future<Void> completion = org.mockito.Mockito.mock(Future.class);
        when(completion.get(anyLong(), eq(TimeUnit.SECONDS))).thenThrow(new TimeoutException());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> AzureSpeechV2StreamingClient.awaitRecognitionStart(completion));

        assertEquals("SPEECH_RECOGNITION_FAILED", exception.getErrorCode().getCode());
    }

    @Test
    void restoresInterruptStatusWhenRecognitionStopIsInterrupted() throws Exception {
        @SuppressWarnings("unchecked")
        Future<Void> completion = org.mockito.Mockito.mock(Future.class);
        when(completion.get(anyLong(), eq(TimeUnit.SECONDS))).thenThrow(new InterruptedException());

        try {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> AzureSpeechV2StreamingClient.awaitRecognitionStop(completion));

            assertEquals("SPEECH_RECOGNITION_FAILED", exception.getErrorCode().getCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
