package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotBlank
        @Size(max = 10000)
        String message
) {
}


