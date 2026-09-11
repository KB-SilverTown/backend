package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.dto.VoiceSessionCreateRequest;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceSessionResponse;

public interface VoiceSessionService {
    VoiceSessionResponse create(String userId, VoiceSessionCreateRequest request);

    VoiceSessionDetailResponse get(String userId, String sessionId);

    VoiceSessionDetailResponse close(String userId, String sessionId);

    /**
     * 음성으로 송금 초안을 만든 뒤, 화면의 최종 확인 절차로 안전하게 넘긴다.
     * 이 경로는 사용자가 송금을 취소한 것이 아니므로 준비된 송금을 취소하지 않는다.
     */
    VoiceSessionDetailResponse handoffToManualConfirmation(String userId, String sessionId);
}
