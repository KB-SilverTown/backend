package com.silvertown.domain.voice.service;

import com.silvertown.domain.voice.enums.VoiceSessionEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class VoiceSessionPromptProvider {
    private static final String GENERAL_FINANCE_PROMPT =
            "안녕하세요. 잔액, 거래 내역, 금융 일정 중 필요한 내용을 말씀해 주세요.";
    private static final String TRANSFER_PROMPT =
            "송금을 도와드릴게요. 받는 분과 금액을 말씀해 주세요.";
    private static final String BILL_PAYMENT_PROMPT =
            "고지서를 화면 안에 맞춰 촬영해주세요.";

    public String firstPromptFor(VoiceSessionEntryPoint entryPoint) {
        switch (entryPoint) {
            case GENERAL_FINANCE:
                return GENERAL_FINANCE_PROMPT;
            case TRANSFER:
                return TRANSFER_PROMPT;
            case BILL_PAYMENT:
                return BILL_PAYMENT_PROMPT;
            default:
                throw new IllegalArgumentException("지원하지 않는 음성 세션 진입점입니다.");
        }
    }
}
