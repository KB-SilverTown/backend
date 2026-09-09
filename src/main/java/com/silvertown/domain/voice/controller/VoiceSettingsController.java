package com.silvertown.domain.voice.controller;

import com.silvertown.domain.voice.dto.VoiceSettingsRequest;
import com.silvertown.domain.voice.dto.VoiceSettingsResponse;
import com.silvertown.domain.voice.service.VoiceSettingsService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "AI Voice")
@RestController
@RequestMapping(value = "/api/users/me/voice-settings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class VoiceSettingsController {

    private final VoiceSettingsService voiceSettingsService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("내 음성 설정 조회")
    @GetMapping
    public ResponseEntity<VoiceSettingsResponse> get(Authentication authentication) {
        String userId = authenticatedUserId.from(authentication).toString();
        return ResponseEntity.ok(voiceSettingsService.get(userId));
    }

    @ApiOperation("내 음성 설정 수정")
    @PutMapping
    public ResponseEntity<VoiceSettingsResponse> update(
            Authentication authentication,
            @Valid @RequestBody VoiceSettingsRequest request
    ) {
        String userId = authenticatedUserId.from(authentication).toString();
        return ResponseEntity.ok(voiceSettingsService.update(
                userId, request));
    }
}
