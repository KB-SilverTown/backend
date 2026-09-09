package com.silvertown.domain.voice.stt;

public interface AzureSpeechRecognitionListener {
    void onPartialTranscript(String transcript);

    void onFinalResult(AzureSpeechDetailedResult result);

    /** Azure가 음성으로 인식할 수 있는 최종 결과를 만들지 못한 경우다. */
    default void onNoMatch() {
        onFailure();
    }

    /** Azure 제공자가 노출하는 안전한 실패 분류를 전달한다. */
    default void onFailure(String providerReason) {
        onFailure();
    }

    void onFailure();
}
