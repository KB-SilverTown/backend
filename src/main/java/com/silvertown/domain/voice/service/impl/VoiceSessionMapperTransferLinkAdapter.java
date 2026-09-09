package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.voice.mapper.VoiceSessionMapper;
import com.silvertown.domain.voice.service.VoiceSessionTransferLinkPort;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoiceSessionMapperTransferLinkAdapter implements VoiceSessionTransferLinkPort {
    private final VoiceSessionMapper voiceSessionMapper;

    @Override
    public void link(UUID userId, UUID voiceSessionId, UUID transferId) {
        if (voiceSessionMapper.updateTransferId(
                userId.toString(), voiceSessionId.toString(), transferId.toString()) != 1) {
            throw new BusinessException(ErrorCode.VOICE_TURN_CONFLICT);
        }
    }
}
