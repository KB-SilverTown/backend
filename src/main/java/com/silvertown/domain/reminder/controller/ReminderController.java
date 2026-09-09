package com.silvertown.domain.reminder.controller;

import com.silvertown.domain.reminder.dto.ReminderCreateRequest;
import com.silvertown.domain.reminder.dto.ReminderListResponse;
import com.silvertown.domain.reminder.dto.ReminderResponse;
import com.silvertown.domain.reminder.dto.ReminderUpdateRequest;
import com.silvertown.domain.reminder.service.ReminderService;
import com.silvertown.global.security.AuthenticatedUserId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@Api(tags = "생활 금융 리마인더")
@RestController
@RequestMapping(value = "/api/reminders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReminderController {
    private final ReminderService reminderService;
    private final AuthenticatedUserId authenticatedUserId;

    @ApiOperation("내 리마인더 목록 조회")
    @ApiResponses({
            @ApiResponse(code = 400, message = "조회 조건이 올바르지 않음"),
            @ApiResponse(code = 401, message = "인증 필요")
    })
    @GetMapping
    public ResponseEntity<ReminderListResponse> find(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication authentication) {
        return ResponseEntity.ok(reminderService.find(
                authenticatedUserId.from(authentication), status, from, to));
    }

    @ApiOperation("리마인더 생성")
    @ApiResponses({
            @ApiResponse(code = 400, message = "예약 시각이 올바르지 않음"),
            @ApiResponse(code = 404, message = "고지서를 찾을 수 없음"),
            @ApiResponse(code = 409, message = "같은 리마인더가 이미 존재함")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReminderResponse> create(
            @RequestBody ReminderCreateRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reminderService.create(
                authenticatedUserId.from(authentication), request));
    }

    @ApiOperation("리마인더 수정")
    @PutMapping(value = "/{reminderId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReminderResponse> update(
            @PathVariable UUID reminderId,
            @RequestBody ReminderUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(reminderService.update(
                authenticatedUserId.from(authentication), reminderId, request));
    }

    @ApiOperation("리마인더 취소")
    @DeleteMapping("/{reminderId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID reminderId, Authentication authentication) {
        reminderService.cancel(authenticatedUserId.from(authentication), reminderId);
        return ResponseEntity.noContent().build();
    }
}
