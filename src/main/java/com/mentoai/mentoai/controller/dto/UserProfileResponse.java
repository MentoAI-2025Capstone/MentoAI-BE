package com.mentoai.mentoai.controller.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record UserProfileResponse(
        Long userId,
        String targetRoleId,
        University university,
        List<String> interestDomains,
        List<Award> awards,
        List<Certification> certifications,
        List<Skill> techStack,
        List<Experience> experiences,
        OffsetDateTime updatedAt
) {

    public record University(String universityName, Integer grade, String major) {
    }

    public record Award(String title, String issuer, LocalDate date, String description, String url) {
    }

    public record Certification(
            String name,
            String issuer,
            String scoreOrLevel,
            LocalDate issueDate,
            LocalDate expireDate,
            String credentialId,
            String url
    ) {
    }

    public record Skill(String name, String level) {
    }

    public record Experience(
            String type,
            String title,
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            Boolean isCurrent,
            String description,
            String url,
            List<String> techStack
    ) {
    }
}




