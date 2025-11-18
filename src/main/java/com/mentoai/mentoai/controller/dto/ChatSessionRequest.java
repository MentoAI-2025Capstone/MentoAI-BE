package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.Size;

public record ChatSessionRequest(
        @Size(max = 200)
        String title
) {
}


