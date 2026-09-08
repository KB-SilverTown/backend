package com.silvertown.domain.recipient.service.impl;
import com.silvertown.domain.recipient.dto.RecipientCandidateRequest;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.recipient.mapper.RecipientMapper;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.domain.recipient.vo.Recipient;
import com.silvertown.global.security.crypto.AccountNumberCrypto;
import com.silvertown.global.security.crypto.AccountNumberMasker;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class RecipientServiceImpl implements RecipientService {
    private final RecipientMapper recipientMapper;
    private final AccountNumberCrypto crypto;
    private final AccountNumberMasker masker;
    public List<RecipientCandidateResponse> findCandidates(UUID userId, RecipientCandidateRequest request) {
        String keyword = request.getKeyword().trim();
        String normalized = keyword.toLowerCase(Locale.ROOT);
        List<RecipientCandidateRequest.ContactInput> contacts = request.getContacts() == null ? Collections.emptyList() : request.getContacts();
        List<String> names = contacts.stream().map(RecipientCandidateRequest.ContactInput::getDisplayName)
                .filter(name -> name != null && name.toLowerCase(Locale.ROOT).contains(normalized))
                .distinct().collect(Collectors.toList());
        return recipientMapper.findCandidates(userId.toString(), keyword, names).stream().map(this::toResponse).collect(Collectors.toList());
    }
    private RecipientCandidateResponse toResponse(Recipient r) {
        return new RecipientCandidateResponse(UUID.fromString(r.getRecipientId()), r.getDisplayName(), r.getRelationship(), r.getBankCode(), masker.mask(crypto.decrypt(r.getAccountNumberEncrypted())), r.getSource(), r.getLastUsedAt());
    }
}
