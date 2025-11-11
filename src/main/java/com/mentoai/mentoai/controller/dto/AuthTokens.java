package com.mentoai.mentoai.controller.dto;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static AuthTokens bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthTokens(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}
