package com.silvertown.domain.voice.controller;

import com.silvertown.domain.voice.dto.SpeechTokenResponse;
import com.silvertown.domain.voice.service.SpeechTokenService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "AI Voice")
@RestController
@RequestMapping(value = "/api/voice", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SpeechTokenController {

    private final SpeechTokenService speechTokenService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("Azure Speech 단기 토큰 발급")
    @PostMapping("/speech-token")
    public ResponseEntity<SpeechTokenResponse> issueToken(Authentication authentication) {
        authenticatedUserId.from(authentication);
        return ResponseEntity.ok(speechTokenService.issueToken());
    }
}
