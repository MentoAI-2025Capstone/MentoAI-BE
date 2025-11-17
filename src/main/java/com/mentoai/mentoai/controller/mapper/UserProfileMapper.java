package com.mentoai.mentoai.controller.mapper;

import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.dto.UserProfileUpsertRequest;
import com.mentoai.mentoai.entity.ExperienceType;
import com.mentoai.mentoai.entity.SkillLevel;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserProfileAwardEntity;
import com.mentoai.mentoai.entity.UserProfileCertificationEntity;
import com.mentoai.mentoai.entity.UserProfileEntity;
import com.mentoai.mentoai.entity.UserProfileExperienceEntity;
import com.mentoai.mentoai.entity.UserProfileSkill;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UserProfileMapper {

    private UserProfileMapper() {
    }

    public static UserProfileResponse toResponse(UserProfileEntity entity) {
        if (entity == null) {
            return null;
        }

        return new UserProfileResponse(
                entity.getUserId(),
                entity.getBirthYear(),
                buildUniversity(entity),
                entity.getInterestDomains() != null ? entity.getInterestDomains() : Collections.emptyList(),
                safeList(entity.getAwards()).stream()
                        .map(award -> new UserProfileResponse.Award(
                                award.getTitle(),
                                award.getIssuer(),
                                award.getDate(),
                                award.getDescription(),
                                award.getUrl()
                        ))
                        .toList(),
                safeList(entity.getCertifications()).stream()
                        .map(cert -> new UserProfileResponse.Certification(
                                cert.getName(),
                                cert.getIssuer(),
                                cert.getScoreOrLevel(),
                                cert.getIssueDate(),
                                cert.getExpireDate(),
                                cert.getCredentialId(),
                                cert.getUrl()
                        ))
                        .toList(),
                safeList(entity.getTechStack()).stream()
                        .map(skill -> new UserProfileResponse.Skill(
                                skill.getName(),
                                skill.getLevel() != null ? skill.getLevel().name() : null
                        ))
                        .toList(),
                safeList(entity.getExperiences()).stream()
                        .map(exp -> new UserProfileResponse.Experience(
                                exp.getType() != null ? exp.getType().name() : null,
                                exp.getTitle(),
                                exp.getOrganization(),
                                exp.getRole(),
                                exp.getStartDate(),
                                exp.getEndDate(),
                                exp.getCurrent(),
                                exp.getDescription(),
                                exp.getUrl(),
                                exp.getTechStack()
                        ))
                        .toList(),
                entity.getUpdatedAt()
        );
    }

    public static UserProfileResponse empty(UserEntity user) {
        return new UserProfileResponse(
                user.getId(),
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );
    }

    public static void apply(UserProfileEntity profile, UserProfileUpsertRequest request) {
        profile.setBirthYear(request.birthYear());

        if (request.university() != null) {
            profile.setUniversityName(request.university().universityName());
            profile.setUniversityGrade(request.university().grade());
            profile.setUniversityMajor(request.university().major());
        } else {
            profile.setUniversityName(null);
            profile.setUniversityGrade(null);
            profile.setUniversityMajor(null);
        }

        // null 체크 추가
        if (profile.getInterestDomains() == null) {
            profile.setInterestDomains(new ArrayList<>());
        }
        // 기존 항목이 있을 때만 clear
        if (!profile.getInterestDomains().isEmpty()) {
            profile.getInterestDomains().clear();
        }
        if (request.interestDomains() != null) {
            profile.getInterestDomains().addAll(
                    request.interestDomains().stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .toList()
            );
        }

        // awards 처리 - 기존 항목이 있을 때만 clear
        if (profile.getAwards() == null) {
            profile.setAwards(new ArrayList<>());
        }
        if (!profile.getAwards().isEmpty()) {
            profile.getAwards().clear();
        }
        if (request.awards() != null) {
            for (UserProfileUpsertRequest.Award awardRequest : request.awards()) {
                UserProfileAwardEntity award = new UserProfileAwardEntity();
                award.setProfile(profile);
                award.setTitle(awardRequest.title());
                award.setIssuer(awardRequest.issuer());
                award.setDate(awardRequest.date());
                award.setDescription(awardRequest.description());
                award.setUrl(awardRequest.url());
                profile.getAwards().add(award);
            }
        }

        // certifications 처리 - 기존 항목이 있을 때만 clear
        if (profile.getCertifications() == null) {
            profile.setCertifications(new ArrayList<>());
        }
        if (!profile.getCertifications().isEmpty()) {
            profile.getCertifications().clear();
        }
        if (request.certifications() != null) {
            for (UserProfileUpsertRequest.Certification certRequest : request.certifications()) {
                UserProfileCertificationEntity cert = new UserProfileCertificationEntity();
                cert.setProfile(profile);
                cert.setName(certRequest.name());
                cert.setIssuer(certRequest.issuer());
                cert.setScoreOrLevel(certRequest.scoreOrLevel());
                cert.setIssueDate(certRequest.issueDate());
                cert.setExpireDate(certRequest.expireDate());
                cert.setCredentialId(certRequest.credentialId());
                cert.setUrl(certRequest.url());
                profile.getCertifications().add(cert);
            }
        }

        // techStack 처리 - 기존 항목이 있을 때만 clear
        if (profile.getTechStack() == null) {
            profile.setTechStack(new ArrayList<>());
        }
        if (!profile.getTechStack().isEmpty()) {
            profile.getTechStack().clear();
        }
        if (request.techStack() != null) {
            for (UserProfileUpsertRequest.Skill skillRequest : request.techStack()) {
                profile.getTechStack().add(UserProfileSkill.builder()
                        .name(skillRequest.name())
                        .level(parseSkillLevel(skillRequest.level()))
                        .build());
            }
        }

        // experiences 처리 - 기존 항목이 있을 때만 clear
        if (profile.getExperiences() == null) {
            profile.setExperiences(new ArrayList<>());
        }
        if (!profile.getExperiences().isEmpty()) {
            profile.getExperiences().clear();
        }
        if (request.experiences() != null) {
            for (UserProfileUpsertRequest.Experience expRequest : request.experiences()) {
                UserProfileExperienceEntity experience = new UserProfileExperienceEntity();
                experience.setProfile(profile);
                experience.setType(parseExperienceType(expRequest.type()));
                experience.setTitle(expRequest.title());
                experience.setOrganization(expRequest.organization());
                experience.setRole(expRequest.role());
                experience.setStartDate(expRequest.startDate());
                experience.setEndDate(expRequest.endDate());
                experience.setCurrent(expRequest.isCurrent());
                experience.setDescription(expRequest.description());
                experience.setUrl(expRequest.url());
                
                // experience의 techStack도 null 체크
                if (experience.getTechStack() == null) {
                    experience.setTechStack(new ArrayList<>());
                }
                experience.getTechStack().clear();
                if (expRequest.techStack() != null) {
                    experience.getTechStack().addAll(
                            expRequest.techStack().stream()
                                    .filter(StringUtils::hasText)
                                    .map(String::trim)
                                    .collect(Collectors.toList())
                    );
                }
                profile.getExperiences().add(experience);
            }
        }

        profile.setUpdatedAt(OffsetDateTime.now());
    }

    private static <T> List<T> safeList(List<T> value) {
        return value != null ? value : Collections.emptyList();
    }

    private static UserProfileResponse.University buildUniversity(UserProfileEntity entity) {
        if (entity.getUniversityName() == null
                && entity.getUniversityGrade() == null
                && entity.getUniversityMajor() == null) {
            return null;
        }
        return new UserProfileResponse.University(
                entity.getUniversityName(),
                entity.getUniversityGrade(),
                entity.getUniversityMajor()
        );
    }

    private static SkillLevel parseSkillLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return null;
        }
        try {
            return SkillLevel.valueOf(level.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("지원하지 않는 스킬 레벨입니다: " + level);
        }
    }

    private static ExperienceType parseExperienceType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return ExperienceType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("지원하지 않는 경험 유형입니다: " + type);
        }
    }
}

