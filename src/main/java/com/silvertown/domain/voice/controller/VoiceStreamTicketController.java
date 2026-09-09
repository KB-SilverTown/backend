package com.silvertown.domain.voice.controller;

import com.silvertown.domain.voice.dto.VoiceStreamTicketResponse;
import com.silvertown.domain.voice.service.VoiceStreamTicketService;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "AI Voice")
@RestController
@RequestMapping(value = "/api/voice/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class VoiceStreamTicketController {
    private static final Pattern CANONICAL_UUID_PATTERN =
            Pattern.compile(VoiceIdentifierPattern.CANONICAL_UUID_REGEX);

    private final VoiceStreamTicketService voiceStreamTicketService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("송금 음성 스트림 ticket 발급")
    @PostMapping("/{sessionId}/stream-ticket")
    public ResponseEntity<VoiceStreamTicketResponse> issue(
            Authentication authentication, @PathVariable("sessionId") String sessionId) {
        String userId = authenticatedUserId.from(authentication).toString();
        VoiceStreamTicketResponse response = voiceStreamTicketService.issue(
                userId, requireCanonicalUuid(sessionId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String requireCanonicalUuid(String identifier) {
        if (!CANONICAL_UUID_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return identifier;
    }
}
