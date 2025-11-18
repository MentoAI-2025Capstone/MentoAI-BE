package com.mentoai.mentoai.controller.dto;

import java.time.OffsetDateTime;

public record ChatMessageResponse(
        Long messageId,
        String role,
        String content,
        OffsetDateTime createdAt
) {
}


