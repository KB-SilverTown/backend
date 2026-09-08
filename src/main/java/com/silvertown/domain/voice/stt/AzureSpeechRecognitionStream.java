package com.silvertown.domain.voice.stt;

public interface AzureSpeechRecognitionStream extends AutoCloseable {
    void write(byte[] audio);

    void stop();

    @Override
    void close();
}
