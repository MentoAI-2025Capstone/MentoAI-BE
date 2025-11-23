package com.mentoai.mentoai.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record UserProfileUpsertRequest(
        String targetRoleId,
        @Valid University university,
        List<String> interestDomains,
        List<@Valid Award> awards,
        List<@Valid Certification> certifications,
        List<@Valid Skill> techStack,
        List<@Valid Experience> experiences
) {

    public record University(String universityName, Integer grade, String major) {
    }

    public record Award(
            @NotNull String title,
            String issuer,
            LocalDate date,
            String description,
            String url
    ) {
    }

    public record Certification(
            @NotNull String name,
            String issuer,
            String scoreOrLevel,
            LocalDate issueDate,
            LocalDate expireDate,
            String credentialId,
            String url
    ) {
    }

    public record Skill(
            @NotNull String name,
            String level
    ) {
    }

    public record Experience(
            @NotNull String type,
            @NotNull String title,
            String organization,
            String role,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean isCurrent,
            String description,
            String url,
            List<String> techStack
    ) {
    }
}




