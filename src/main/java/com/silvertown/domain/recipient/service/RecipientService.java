package com.silvertown.domain.recipient.service;
import com.silvertown.domain.recipient.dto.RecipientCandidateRequest;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import java.util.List;
import java.util.UUID;
public interface RecipientService {
    List<RecipientCandidateResponse> findCandidates(UUID userId, RecipientCandidateRequest request);
}
