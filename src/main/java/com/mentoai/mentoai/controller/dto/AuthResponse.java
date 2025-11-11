package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.controller.mapper.UserMapper;
import com.mentoai.mentoai.entity.UserEntity;

public record AuthResponse(UserSummary user, AuthTokens tokens) {
    public static AuthResponse of(UserEntity userEntity, AuthTokens tokens) {
        return new AuthResponse(UserMapper.toSummary(userEntity), tokens);
    }
}
