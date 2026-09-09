package com.silvertown.domain.voice.service;

import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import java.util.List;
import java.util.UUID;

/** Voice-domain boundary for recipient discovery. */
public interface RecipientCandidatePort {
    List<RecipientCandidateResponse> findCandidates(UUID userId, String keyword);
}
