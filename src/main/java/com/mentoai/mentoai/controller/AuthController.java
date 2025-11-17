package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.AuthTokens;
import com.mentoai.mentoai.controller.dto.AuthStatus;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.security.UserPrincipal;
import com.mentoai.mentoai.service.AuthService;
import com.mentoai.mentoai.service.UserService;
import com.mentoai.mentoai.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserProfileService userProfileService;

    @GetMapping("/google/start")
    public ResponseEntity<Void> startGoogleOAuth(
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request
    ) {
        authService.rememberFrontendRedirect(request, redirectUri);
        URI redirect = authService.buildAuthorizationRedirect(request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirect)
                .build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<?> googleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "redirect") String mode,
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request
    ) {
        return authService.handleCallback(code, state, mode, redirectUri, request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokens> refresh(@RequestBody @Valid RefreshRequest request) {
        AuthTokens tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(tokens);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthStatus> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(AuthStatus.anonymous());
        }
        UserEntity user = userService.getUser(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        boolean profileComplete = userProfileService.isProfileComplete(principal.id());
        return ResponseEntity.ok(AuthStatus.authenticated(user, profileComplete));
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
