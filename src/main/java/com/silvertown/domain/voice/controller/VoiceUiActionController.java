package com.silvertown.domain.voice.controller;

import com.silvertown.domain.voice.dto.VoiceUiActionRequest;
import com.silvertown.domain.voice.dto.VoiceUiActionResponse;
import com.silvertown.domain.voice.service.VoiceUiActionService;
import com.silvertown.domain.voice.validation.VoiceIdentifierPattern;
import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.regex.Pattern;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "AI Voice")
@RestController
@RequestMapping("/api/voice/sessions")
@RequiredArgsConstructor
public class VoiceUiActionController {
    private static final Pattern CANONICAL_UUID_PATTERN =
            Pattern.compile(VoiceIdentifierPattern.CANONICAL_UUID_REGEX);

    private final VoiceUiActionService voiceUiActionService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("음성 상호작용 카드 화면 액션 처리")
    @PostMapping(value = "/{sessionId}/ui-actions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VoiceUiActionResponse> process(
            Authentication authentication,
            @PathVariable("sessionId") String sessionId,
            @Valid @RequestBody VoiceUiActionRequest request) {
        if (!CANONICAL_UUID_PATTERN.matcher(sessionId).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(voiceUiActionService.process(
                authenticatedUserId.from(authentication).toString(), sessionId, request));
    }
}
