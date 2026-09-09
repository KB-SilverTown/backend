package com.silvertown.domain.voice.stt;

import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.OutputFormat;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Azure Speech v2 WebSocket adapter for 16kHz/16-bit/mono PCM transfer audio.
 *
 * <p>The SDK owns the provider WebSocket. The application receives only a final transcript,
 * rank-one confidence, and ordered N-best alternatives through {@link AzureSpeechDetailedResult}.</p>
 */
@Slf4j
@Component
public class AzureSpeechV2StreamingClient {
    private static final long START_TIMEOUT_SECONDS = 10;
    private static final long STOP_TIMEOUT_SECONDS = 5;
    private static final String V2_ENDPOINT_TEMPLATE =
            "wss://%s.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v2";

    private final AzureSpeechDetailedResultNormalizer resultNormalizer;
    private final String subscriptionKey;
    private final String region;
    private final String recognitionLanguage;

    public AzureSpeechV2StreamingClient(
            AzureSpeechDetailedResultNormalizer resultNormalizer,
            @Value("${azure.speech.key:}") String subscriptionKey,
            @Value("${azure.speech.region:}") String region,
            @Value("${azure.speech.recognition-language:ko-KR}") String recognitionLanguage) {
        this.resultNormalizer = resultNormalizer;
        this.subscriptionKey = subscriptionKey;
        this.region = region;
        this.recognitionLanguage = recognitionLanguage;
    }

    public AzureSpeechRecognitionStream open(AzureSpeechRecognitionListener listener) {
        if (listener == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        SpeechConfig speechConfig = null;
        PushAudioInputStream audioStream = null;
        AudioConfig audioConfig = null;
        SpeechRecognizer recognizer = null;
        try {
            speechConfig = SpeechConfig.fromEndpoint(v2Endpoint(), subscriptionKey.trim());
            speechConfig.setOutputFormat(OutputFormat.Detailed);
            speechConfig.setSpeechRecognitionLanguage(recognitionLanguage.trim());

            AudioStreamFormat audioFormat = AudioStreamFormat.getWaveFormatPCM(16_000, (short) 16, (short) 1);
            audioStream = AudioInputStream.createPushStream(audioFormat);
            audioConfig = AudioConfig.fromStreamInput(audioStream);
            recognizer = new SpeechRecognizer(speechConfig, audioConfig);
            recognizer.recognizing.addEventListener((sender, event) -> {
                String transcript = event.getResult().getText();
                if (!isBlank(transcript)) {
                    listener.onPartialTranscript(transcript);
                }
            });
            recognizer.recognized.addEventListener((sender, event) -> {
                if (event.getResult().getReason() == ResultReason.NoMatch) {
                    log.info("Azure Speech v2 did not recognize a final utterance.");
                    listener.onNoMatch();
                    return;
                }
                if (event.getResult().getReason() != ResultReason.RecognizedSpeech) {
                    return;
                }
                listener.onFinalResult(resultNormalizer.normalize(
                        event.getResult().getText(),
                        event.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult)));
            });
            recognizer.canceled.addEventListener((sender, event) -> {
                if (event.getReason() == CancellationReason.Error) {
                    String providerReason = "CANCELLED_" + event.getErrorCode();
                    log.warn("Azure Speech v2 recognition was cancelled. reason={}, errorCode={}",
                            event.getReason(), event.getErrorCode());
                    listener.onFailure(providerReason);
                }
            });
            awaitRecognitionStart(recognizer.startContinuousRecognitionAsync());
            return new SpeechSdkRecognitionStream(recognizer, audioConfig, audioStream, speechConfig);
        } catch (BusinessException exception) {
            closeQuietly(recognizer, audioConfig, audioStream, speechConfig);
            throw exception;
        } catch (Exception exception) {
            closeQuietly(recognizer, audioConfig, audioStream, speechConfig);
            log.warn("Unable to open Azure Speech v2 recognition stream.");
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        }
    }

    private void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception exception) {
                log.warn("Unable to release Azure Speech resource after stream open failure.");
            }
        }
    }

    private URI v2Endpoint() {
        if (isBlank(subscriptionKey) || isBlank(region) || isBlank(recognitionLanguage)
                || !region.trim().matches("[a-z0-9-]+")) {
            throw new BusinessException(ErrorCode.SPEECH_NOT_CONFIGURED);
        }
        return URI.create(V2_ENDPOINT_TEMPLATE.formatted(region.trim().toLowerCase(Locale.ROOT)));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static void awaitRecognitionStop(Future<Void> completion) {
        try {
            completion.get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        }
    }

    static void awaitRecognitionStart(Future<Void> completion) {
        try {
            completion.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SPEECH_RECOGNITION_FAILED);
        }
    }

    private static class SpeechSdkRecognitionStream implements AzureSpeechRecognitionStream {
        private final SpeechRecognizer recognizer;
        private final AudioConfig audioConfig;
        private final PushAudioInputStream audioStream;
        private final SpeechConfig speechConfig;
        private boolean closed;

        private SpeechSdkRecognitionStream(
                SpeechRecognizer recognizer,
                AudioConfig audioConfig,
                PushAudioInputStream audioStream,
                SpeechConfig speechConfig) {
            this.recognizer = recognizer;
            this.audioConfig = audioConfig;
            this.audioStream = audioStream;
            this.speechConfig = speechConfig;
        }

        @Override
        public synchronized void write(byte[] audio) {
            if (closed || audio == null || audio.length == 0) {
                return;
            }
            audioStream.write(audio);
        }

        @Override
        public synchronized void stop() {
            if (closed) {
                return;
            }
            try {
                audioStream.close();
                awaitRecognitionStop(recognizer.stopContinuousRecognitionAsync());
            } finally {
                close();
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            recognizer.close();
            audioConfig.close();
            audioStream.close();
            speechConfig.close();
        }
    }
}
