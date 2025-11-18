package com.mentoai.mentoai.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatSessionResponse(
        Long sessionId,
        Long userId,
        String title,
        List<ChatMessageResponse> messages,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}


