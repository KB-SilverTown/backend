package com.silvertown.domain.voice.service.impl;

import com.silvertown.domain.recipient.dto.RecipientCandidateRequest;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.domain.voice.service.RecipientCandidatePort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecipientServiceRecipientCandidateAdapter implements RecipientCandidatePort {
    private final RecipientService recipientService;

    @Override
    public List<RecipientCandidateResponse> findCandidates(UUID userId, String keyword) {
        RecipientCandidateRequest request = new RecipientCandidateRequest();
        request.setKeyword(keyword);
        return recipientService.findCandidates(userId, request);
    }
}
