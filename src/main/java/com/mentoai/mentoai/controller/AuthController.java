package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.AuthResponse;
import com.mentoai.mentoai.controller.dto.AuthTokens;
import com.mentoai.mentoai.controller.dto.UserSummary;
import com.mentoai.mentoai.controller.mapper.UserMapper;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.security.UserPrincipal;
import com.mentoai.mentoai.service.AuthService;
import com.mentoai.mentoai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/google/start")
    public ResponseEntity<Void> startGoogleOAuth(HttpServletRequest request) {
        URI redirect = authService.buildAuthorizationRedirect(request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirect)
                .build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        return authService.handleCallback(code, state, request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokens> refresh(@RequestBody @Valid RefreshRequest request) {
        AuthTokens tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(tokens);
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserEntity user = userService.getUser(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return ResponseEntity.ok(UserMapper.toSummary(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            userService.getUser(principal.id()).ifPresent(authService::logout);
        }
        return ResponseEntity.noContent().build();
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
