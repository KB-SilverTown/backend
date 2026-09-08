package com.silvertown.domain.recipient.controller;
import com.silvertown.domain.recipient.dto.RecipientCandidateRequest;
import com.silvertown.domain.recipient.dto.RecipientCandidateResponse;
import com.silvertown.domain.recipient.service.RecipientService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Api(tags = "수취인") @RestController @RequestMapping("/api/recipients") @RequiredArgsConstructor
public class RecipientController {
    private final RecipientService recipientService;
    private final AuthenticatedUserId authenticatedUserId;
    @ApiOperation("수취인 후보 조회") @PostMapping("/candidates")
    public ResponseEntity<List<RecipientCandidateResponse>> findCandidates(@Valid @RequestBody RecipientCandidateRequest request, Authentication authentication) {
        return ResponseEntity.ok(recipientService.findCandidates(authenticatedUserId.from(authentication), request));
    }
}
