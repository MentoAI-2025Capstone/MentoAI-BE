package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.ChatMessageRequest;
import com.mentoai.mentoai.controller.dto.ChatMessageResponse;
import com.mentoai.mentoai.controller.dto.ChatSessionRequest;
import com.mentoai.mentoai.controller.dto.ChatSessionResponse;
import com.mentoai.mentoai.security.UserPrincipal;
import com.mentoai.mentoai.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Tag(name = "chat", description = "AI 챗봇 대화 API")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    @Operation(summary = "새 채팅 세션 생성")
    public ResponseEntity<ChatSessionResponse> createSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatSessionRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ChatSessionResponse session = chatService.createSession(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "메시지 전송 및 AI 응답 받기")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatMessageRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ChatMessageResponse response = chatService.sendMessage(sessionId, principal.id(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "사용자의 채팅 세션 목록 조회")
    public ResponseEntity<List<ChatSessionResponse>> getUserSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ChatSessionResponse> sessions = chatService.getUserSessions(principal.id());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "채팅 세션 상세 조회 (메시지 포함)")
    public ResponseEntity<ChatSessionResponse> getSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ChatSessionResponse session = chatService.getSession(sessionId, principal.id());
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "채팅 세션 삭제")
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long sessionId) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        chatService.deleteSession(sessionId, principal.id());
        return ResponseEntity.noContent().build();
    }
}


