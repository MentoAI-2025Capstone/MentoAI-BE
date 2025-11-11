package com.mentoai.mentoai.controller.dto;

public record UserSummary(
        Long userId,
        String name,
        String nickname,
        String email,
        String provider,
        String profileImageUrl
) {
}
