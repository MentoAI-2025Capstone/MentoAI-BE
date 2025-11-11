package com.mentoai.mentoai.service;

import com.mentoai.mentoai.config.AuthProperties;
import com.mentoai.mentoai.controller.dto.AuthResponse;
import com.mentoai.mentoai.controller.dto.AuthTokens;
import com.mentoai.mentoai.controller.dto.GoogleTokenResponse;
import com.mentoai.mentoai.controller.dto.GoogleUserInfo;
import com.mentoai.mentoai.entity.RefreshTokenEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.repository.RefreshTokenRepository;
import com.mentoai.mentoai.security.JwtTokenProvider;
import com.mentoai.mentoai.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.ResponseEntity;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String OAUTH_STATE_SESSION_KEY = "OAUTH_STATE";

    private final GoogleOAuthClient googleOAuthClient;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    public URI buildAuthorizationRedirect(HttpServletRequest request) {
        String state = generateState();
        request.getSession(true).setAttribute(OAUTH_STATE_SESSION_KEY, state);
        boolean useLocal = isLocalRequest(request);
        return googleOAuthClient.buildAuthorizationUri(state, useLocal);
    }

    @Transactional
    public ResponseEntity<Void> handleCallback(String code, String state, HttpServletRequest request) {
        validateState(state, request.getSession(false));
        boolean useLocal = isLocalRequest(request);

        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeCodeForToken(code, useLocal);
        GoogleUserInfo userInfo = googleOAuthClient.fetchUserInfo(tokenResponse.accessToken());

        UserEntity user = userService.upsertOAuthUser(
                UserEntity.AuthProvider.GOOGLE,
                userInfo.id(),
                userInfo.email(),
                userInfo.name(),
                null,
                userInfo.picture()
        );

        AuthTokens tokens = issueTokens(user);
        clearState(request.getSession(false));

        URI redirectUri = buildFrontendRedirect(tokens);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(redirectUri);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private AuthTokens issueTokens(UserEntity user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        OffsetDateTime refreshExpiry = jwtTokenProvider.calculateRefreshTokenExpiry();

        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(refreshExpiry)
                .build());

        return AuthTokens.bearer(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpirySeconds());
    }

    public Optional<RefreshTokenEntity> findValidRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .filter(rt -> rt.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        RefreshTokenEntity refreshTokenEntity = findValidRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("유효하지 않은 리프레시 토큰입니다."));

        UserEntity user = refreshTokenEntity.getUser();
        return issueTokens(user);
    }

    @Transactional
    public void logout(UserEntity user) {
        refreshTokenRepository.deleteByUser(user);
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateState(String state, HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("잘못된 인증 상태입니다.");
        }
        String expected = (String) session.getAttribute(OAUTH_STATE_SESSION_KEY);
        if (!StringUtils.hasText(state) || expected == null || !expected.equals(state)) {
            throw new IllegalArgumentException("잘못된 state 값입니다.");
        }
    }

    private void clearState(HttpSession session) {
        if (session != null) {
            session.removeAttribute(OAUTH_STATE_SESSION_KEY);
        }
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String host = request.getHeader("host");
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
        }
        return host != null && host.contains("localhost");
    }

    private URI buildFrontendRedirect(AuthTokens tokens) {
        return UriComponentsBuilder.fromUriString(googleOAuthClient.getFrontendCallbackUri())
                .fragment(String.format("accessToken=%s&refreshToken=%s&tokenType=%s&expiresIn=%d",
                        tokens.accessToken(),
                        tokens.refreshToken(),
                        tokens.tokenType(),
                        tokens.expiresIn()))
                .build(true)
                .toUri();
    }
}
