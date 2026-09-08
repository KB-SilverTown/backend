package com.silvertown.domain.voice.stt;

public interface AzureSpeechRecognitionListener {
    void onPartialTranscript(String transcript);

    void onFinalResult(AzureSpeechDetailedResult result);

    void onFailure();
}
