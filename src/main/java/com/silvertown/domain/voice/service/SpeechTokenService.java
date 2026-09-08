package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.SpeechTokenResponse;

public interface SpeechTokenService {
    SpeechTokenResponse issueToken();
}
