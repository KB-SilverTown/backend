package com.silvertown.domain.voice.controller;

import com.silvertown.domain.voice.dto.VoiceSessionCreateRequest;
import com.silvertown.domain.voice.dto.VoiceSessionDetailResponse;
import com.silvertown.domain.voice.dto.VoiceSessionResponse;
import com.silvertown.domain.voice.service.VoiceSessionService;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.regex.Pattern;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "AI Voice")
@RestController
@RequestMapping(value = "/api/voice/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class VoiceSessionController {
    private static final Pattern CANONICAL_UUID_PATTERN =
            Pattern.compile(VoiceIdentifierPattern.CANONICAL_UUID_REGEX);

    private final VoiceSessionService voiceSessionService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("음성 세션 생성")
    @PostMapping
    public ResponseEntity<VoiceSessionResponse> create(
            Authentication authentication,
            @Valid @RequestBody VoiceSessionCreateRequest request
    ) {
        String userId = authenticatedUserId.from(authentication).toString();
        VoiceSessionResponse response = voiceSessionService.create(
                userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ApiOperation("음성 세션 현재 상태 조회")
    @GetMapping("/{sessionId}")
    public ResponseEntity<VoiceSessionDetailResponse> get(
            Authentication authentication,
            @PathVariable("sessionId") String sessionId
    ) {
        String userId = authenticatedUserId.from(authentication).toString();
        return ResponseEntity.ok(voiceSessionService.get(
                userId, requireCanonicalUuid(sessionId)));
    }

    @ApiOperation("음성 세션 종료")
    @PostMapping("/{sessionId}/close")
    public ResponseEntity<VoiceSessionDetailResponse> close(
            Authentication authentication,
            @PathVariable("sessionId") String sessionId
    ) {
        String userId = authenticatedUserId.from(authentication).toString();
        return ResponseEntity.ok(voiceSessionService.close(
                userId, requireCanonicalUuid(sessionId)));
    }

    private String requireCanonicalUuid(String identifier) {
        if (!CANONICAL_UUID_PATTERN.matcher(identifier).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return identifier;
    }
}
