package com.mentoai.mentoai.controller.dto;

public record GoogleUserInfo(
        String id,
        String email,
        String name,
        String given_name,
        String family_name,
        String picture
) {
}
