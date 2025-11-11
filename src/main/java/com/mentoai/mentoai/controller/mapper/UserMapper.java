package com.mentoai.mentoai.controller.mapper;

import com.mentoai.mentoai.controller.dto.UserSummary;
import com.mentoai.mentoai.entity.UserEntity;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserSummary toSummary(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return new UserSummary(
                entity.getId(),
                entity.getName(),
                entity.getNickname(),
                entity.getEmail(),
                entity.getAuthProvider() != null ? entity.getAuthProvider().name() : null,
                entity.getProfileImageUrl()
        );
    }
}
