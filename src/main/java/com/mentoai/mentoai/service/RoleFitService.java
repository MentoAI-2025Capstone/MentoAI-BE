package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ImprovementItem;
import com.mentoai.mentoai.controller.dto.RoleFitBatchRequest;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.RoleFitResponse;
import com.mentoai.mentoai.controller.dto.RoleFitSimulationRequest;
import com.mentoai.mentoai.controller.dto.RoleFitSimulationResponse;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.SkillLevel;
import com.mentoai.mentoai.entity.TargetRoleEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserProfileCertificationEntity;
import com.mentoai.mentoai.entity.UserProfileEntity;
import com.mentoai.mentoai.entity.UserProfileExperienceEntity;
import com.mentoai.mentoai.entity.UserProfileSkill;
import com.mentoai.mentoai.entity.WeightedMajor;
import com.mentoai.mentoai.entity.WeightedSkill;
import com.mentoai.mentoai.repository.TargetRoleRepository;
import com.mentoai.mentoai.repository.UserProfileRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleFitService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TargetRoleRepository targetRoleRepository;
    private final RecommendService recommendService;

    public RoleFitResponse calculateRoleFit(Long userId, RoleFitRequest request) {
        UserEntity user = getUser(userId);
        UserProfileEntity profile = userProfileRepository.findById(userId).orElse(null);
        
        String targetRoleId = resolveTarget(request.target());
        Optional<TargetRoleEntity> targetRoleOpt = targetRoleRepository.findById(targetRoleId);
        TargetRoleEntity targetRole = targetRoleOpt.orElse(null);

        double skillFit = calculateSkillFit(profile, targetRole);
        double experienceFit = calculateExperienceFit(profile, targetRole);
        double educationFit = calculateEducationFit(profile, targetRole);
        double evidenceFit = calculateEvidenceFit(profile, targetRole);

        // RoleFitScore = 0.50 * SkillFit + 0.30 * ExperienceFit + 0.15 * EducationFit + 0.05 * EvidenceFit
        double roleFitScore = roundScore((0.50 * skillFit + 0.30 * experienceFit + 0.15 * educationFit + 0.05 * evidenceFit) * 100);

        List<RoleFitResponse.MissingSkill> missingSkills = buildMissingSkills(profile, targetRole);
        List<String> recommendations = buildRecommendations(targetRole);

        return new RoleFitResponse(
                targetRoleId,
                roleFitScore,
                new RoleFitResponse.Breakdown(skillFit, experienceFit, educationFit, evidenceFit),
                missingSkills,
                recommendations
        );
    }

    public List<RoleFitResponse> calculateRoleFitBatch(Long userId, RoleFitBatchRequest request) {
        List<String> targets = request.targets() != null && !request.targets().isEmpty()
                ? request.targets()
                : List.of("general");

        return targets.stream()
                .map(target -> calculateRoleFit(userId, new RoleFitRequest(target, request.topNImprovements())))
                .toList();
    }

    public RoleFitSimulationResponse simulateRoleFit(Long userId, RoleFitSimulationRequest request) {
        RoleFitResponse base = calculateRoleFit(userId, new RoleFitRequest(request.target(), null));

        double skillDelta = clamp((request.addSkills() != null ? request.addSkills().size() : 0) * 0.03);
        double experienceDelta = clamp((request.addExperiences() != null ? request.addExperiences().size() : 0) * 0.025);
        double evidenceDelta = clamp((request.addCertifications() != null ? request.addCertifications().size() : 0) * 0.02);
        double educationDelta = 0.0;

        RoleFitResponse.Breakdown deltaBreakdown = new RoleFitResponse.Breakdown(
                skillDelta,
                experienceDelta,
                educationDelta,
                evidenceDelta
        );

        double baseScore = base.roleFitScore();
        double newScore = roundScore(baseScore + (skillDelta + experienceDelta + educationDelta + evidenceDelta) * 25);

        return new RoleFitSimulationResponse(
                baseScore,
                newScore,
                roundScore(newScore - baseScore),
                deltaBreakdown
        );
    }

    public List<ImprovementItem> recommendImprovements(Long userId, String roleId, int size) {
        List<ActivityEntity> activities = fetchImprovementActivities(userId, size);
        String affects = StringUtils.hasText(roleId) && roleId.toLowerCase(Locale.ROOT).contains("backend")
                ? "SKILL"
                : "EXPERIENCE";

        double step = size > 0 ? 5.0 / size : 1.0;
        List<ImprovementItem> items = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            ActivityEntity activity = activities.get(i);
            double delta = roundScore(Math.max(1.0, 3.0 + (size - i) * step / 2));
            items.add(new ImprovementItem(
                    activity.getType() != null ? activity.getType().name() : "STUDY",
                    ActivityMapper.toResponse(activity),
                    delta,
                    List.of(affects),
                    buildImprovementReason(activity, affects)
            ));
        }
        return items;
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private double calculateSkillFit(UserProfileEntity profile, TargetRoleEntity targetRole) {
        if (profile == null || profile.getTechStack() == null || profile.getTechStack().isEmpty()) {
            return 0.0;
        }

        if (targetRole == null || targetRole.getRequiredSkills() == null || targetRole.getRequiredSkills().isEmpty()) {
            return 0.0;
        }

        List<UserProfileSkill> userSkills = profile.getTechStack();
        List<WeightedSkill> requiredSkills = targetRole.getRequiredSkills();
        List<WeightedSkill> allTargetSkills = new ArrayList<>(requiredSkills);
        if (targetRole.getBonusSkills() != null) {
            allTargetSkills.addAll(targetRole.getBonusSkills());
        }

        // 사용자 스킬을 맵으로 변환 (스킬 레벨을 숫자로 변환)
        Map<String, Double> userSkillMap = userSkills.stream()
                .collect(Collectors.toMap(
                        skill -> skill.getName() != null ? skill.getName().toLowerCase(Locale.ROOT) : "",
                        skill -> skillLevelToNumber(skill.getLevel()),
                        (a, b) -> Math.max(a, b),
                        java.util.LinkedHashMap::new
                ));

        // Coverage 계산: coverage = Σ(min(userLevel, reqWeight)) / Σ(reqWeight)
        double coverageSum = 0.0;
        double reqWeightSum = 0.0;

        for (WeightedSkill required : requiredSkills) {
            if (required.getWeight() == null || required.getWeight() <= 0) {
                continue;
            }
            double reqWeight = required.getWeight();
            reqWeightSum += reqWeight;
            
            String skillName = required.getName() != null ? required.getName().toLowerCase(Locale.ROOT) : "";
            Double userLevel = userSkillMap.get(skillName);
            
            if (userLevel != null) {
                coverageSum += Math.min(userLevel, reqWeight);
            }
        }

        double coverage = reqWeightSum > 0 ? coverageSum / reqWeightSum : 0.0;

        // Cosine similarity 계산: cosine_similarity(userSkillVector, targetSkillVector)
        double cosine = calculateCosineSimilarity(userSkillMap, allTargetSkills);

        // SkillFit = 0.7 * coverage + 0.3 * cosine
        return clamp(0.7 * coverage + 0.3 * cosine);
    }

    private double skillLevelToNumber(SkillLevel level) {
        if (level == null) {
            return 0.0;
        }
        return switch (level) {
            case BEGINNER -> 0.5;
            case INTERMEDIATE -> 0.75;
            case ADVANCED -> 1.0;
            case EXPERT -> 1.2;
        };
    }

    private double calculateCosineSimilarity(Map<String, Double> userSkills, List<WeightedSkill> targetSkills) {
        if (userSkills.isEmpty() || targetSkills.isEmpty()) {
            return 0.0;
        }

        // 모든 고유 스킬 이름 수집
        java.util.Set<String> allSkillNames = new java.util.HashSet<>();
        allSkillNames.addAll(userSkills.keySet());
        for (WeightedSkill skill : targetSkills) {
            if (skill.getName() != null) {
                allSkillNames.add(skill.getName().toLowerCase(Locale.ROOT));
            }
        }

        // 벡터 생성
        List<Double> userVector = new ArrayList<>();
        List<Double> targetVector = new ArrayList<>();

        for (String skillName : allSkillNames) {
            userVector.add(userSkills.getOrDefault(skillName, 0.0));
            
            double targetValue = 0.0;
            for (WeightedSkill skill : targetSkills) {
                if (skill.getName() != null && skill.getName().toLowerCase(Locale.ROOT).equals(skillName)) {
                    targetValue += skill.getWeight() != null ? skill.getWeight() : 0.0;
                }
            }
            targetVector.add(targetValue);
        }

        // Cosine similarity 계산
        double dotProduct = 0.0;
        double userNorm = 0.0;
        double targetNorm = 0.0;

        for (int i = 0; i < userVector.size(); i++) {
            double u = userVector.get(i);
            double t = targetVector.get(i);
            dotProduct += u * t;
            userNorm += u * u;
            targetNorm += t * t;
        }

        double denominator = Math.sqrt(userNorm) * Math.sqrt(targetNorm);
        return denominator > 0 ? dotProduct / denominator : 0.0;
    }

    private double calculateExperienceFit(UserProfileEntity profile, TargetRoleEntity targetRole) {
        if (profile == null || profile.getExperiences() == null || profile.getExperiences().isEmpty()) {
            return 0.0;
        }

        // 타겟 역할의 스킬 유니버스 생성
        java.util.Set<String> targetSkillUniverse = new java.util.HashSet<>();
        if (targetRole != null) {
            if (targetRole.getRequiredSkills() != null) {
                for (WeightedSkill skill : targetRole.getRequiredSkills()) {
                    if (skill.getName() != null) {
                        targetSkillUniverse.add(skill.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (targetRole.getBonusSkills() != null) {
                for (WeightedSkill skill : targetRole.getBonusSkills()) {
                    if (skill.getName() != null) {
                        targetSkillUniverse.add(skill.getName().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        List<UserProfileExperienceEntity> experiences = profile.getExperiences();
        double totalExperienceFit = 0.0;

        // ExperienceFit = Σ(rel * months * typeWeight)
        for (UserProfileExperienceEntity exp : experiences) {
            if (exp == null) {
                continue;
            }

            // rel = overlap_ratio(exp.techStack, targetRole.skillUniverse)
            double rel = calculateOverlapRatio(exp.getTechStack(), targetSkillUniverse);

            // months = clamp(duration_months / 24, 0, 1)
            double months = 0.0;
            if (exp.getStartDate() != null) {
                LocalDate endDate = exp.getEndDate() != null ? exp.getEndDate() : LocalDate.now();
                long durationMonths = ChronoUnit.MONTHS.between(exp.getStartDate(), endDate);
                months = clamp(durationMonths / 24.0);
            }

            // typeWeight = {PROJECT: 1.0, INTERNSHIP: 1.0, RESEARCH: 0.8, PARTTIME: 0.6}
            double typeWeight = getExperienceTypeWeight(exp.getType());

            totalExperienceFit += rel * months * typeWeight;
        }

        return clamp(totalExperienceFit);
    }

    private double calculateOverlapRatio(List<String> expTechStack, java.util.Set<String> targetSkillUniverse) {
        if (expTechStack == null || expTechStack.isEmpty() || targetSkillUniverse.isEmpty()) {
            return 0.0;
        }

        long overlapCount = expTechStack.stream()
                .map(tech -> tech != null ? tech.toLowerCase(Locale.ROOT) : "")
                .filter(tech -> targetSkillUniverse.stream()
                        .anyMatch(target -> tech.contains(target) || target.contains(tech)))
                .count();

        return (double) overlapCount / expTechStack.size();
    }

    private double getExperienceTypeWeight(com.mentoai.mentoai.entity.ExperienceType type) {
        if (type == null) {
            return 0.5;
        }
        return switch (type) {
            case PROJECT -> 1.0;
            case INTERNSHIP -> 1.0;
            case UNDERGRAD_RESEARCH -> 0.8;
            case PARTTIME -> 0.6;
            default -> 0.5;
        };
    }

    private double calculateEducationFit(UserProfileEntity profile, TargetRoleEntity targetRole) {
        if (profile == null) {
            return 0.0;
        }

        // majorMatch = targetRole.majorMapping.get(user.major, 0.5)
        double majorMatch = 0.5; // 기본값
        if (targetRole != null && targetRole.getMajorMapping() != null && !targetRole.getMajorMapping().isEmpty()) {
            String userMajor = profile.getUniversityMajor();
            if (StringUtils.hasText(userMajor)) {
                String userMajorLower = userMajor.toLowerCase(Locale.ROOT);
                for (WeightedMajor majorMapping : targetRole.getMajorMapping()) {
                    if (majorMapping.getMajor() != null && 
                        userMajorLower.contains(majorMapping.getMajor().toLowerCase(Locale.ROOT))) {
                        majorMatch = majorMapping.getWeight() != null ? majorMapping.getWeight() : 0.5;
                        break; // 첫 번째 매칭만 사용
                    }
                }
            }
        }

        // seniorityMatch = expectedSeniorityScore(user.grade, targetRole.expectedSeniority)
        double seniorityMatch = calculateSeniorityMatch(profile.getUniversityGrade(), 
                targetRole != null ? targetRole.getExpectedSeniority() : null);

        // EducationFit = 0.7 * majorMatch + 0.3 * seniorityMatch
        return clamp(0.7 * majorMatch + 0.3 * seniorityMatch);
    }

    private double calculateSeniorityMatch(Integer userGrade, String expectedSeniority) {
        if (userGrade == null || userGrade <= 0) {
            return 0.0;
        }

        if (!StringUtils.hasText(expectedSeniority)) {
            // 기대 시니어리티가 없으면 학년만으로 계산
            return clamp(userGrade / 4.0);
        }

        String seniorityLower = expectedSeniority.toLowerCase(Locale.ROOT);
        
        // ENTRY, JUNIOR, MID, SENIOR 등의 매핑
        if (seniorityLower.contains("entry") || seniorityLower.contains("junior")) {
            // 1-2학년이 적합
            return userGrade <= 2 ? 1.0 : Math.max(0.0, 1.0 - (userGrade - 2) * 0.3);
        } else if (seniorityLower.contains("mid") || seniorityLower.contains("middle")) {
            // 2-3학년이 적합
            return userGrade >= 2 && userGrade <= 3 ? 1.0 : 
                   userGrade < 2 ? 0.7 : Math.max(0.0, 1.0 - (userGrade - 3) * 0.3);
        } else if (seniorityLower.contains("senior")) {
            // 3-4학년이 적합
            return userGrade >= 3 ? 1.0 : userGrade * 0.3;
        }

        // 기본값: 학년에 비례
        return clamp(userGrade / 4.0);
    }

    private double calculateEvidenceFit(UserProfileEntity profile, TargetRoleEntity targetRole) {
        if (profile == null) {
            return 0.0;
        }

        // cert = overlap_ratio(user.certifications, targetRole.recommendedCerts)
        double cert = 0.0;
        if (targetRole != null && targetRole.getRecommendedCerts() != null && !targetRole.getRecommendedCerts().isEmpty()) {
            List<String> userCerts = profile.getCertifications() != null
                    ? profile.getCertifications().stream()
                            .map(c -> c != null && StringUtils.hasText(c.getName()) ? c.getName().toLowerCase(Locale.ROOT) : "")
                            .filter(StringUtils::hasText)
                            .toList()
                    : List.of();
            
            List<String> recommendedCerts = targetRole.getRecommendedCerts().stream()
                    .map(c -> c != null ? c.toLowerCase(Locale.ROOT) : "")
                    .filter(StringUtils::hasText)
                    .toList();

            if (!userCerts.isEmpty() && !recommendedCerts.isEmpty()) {
                long matchedCount = userCerts.stream()
                        .filter(uc -> recommendedCerts.stream()
                                .anyMatch(rc -> uc.contains(rc) || rc.contains(uc)))
                        .count();
                cert = (double) matchedCount / recommendedCerts.size();
            }
        }

        // portfolio = hasUrlsInRelatedExperiences(user.experiences) ? 1 : 0
        double portfolio = hasUrlsInRelatedExperiences(profile.getExperiences()) ? 1.0 : 0.0;

        // EvidenceFit = 0.7 * cert + 0.3 * portfolio
        return clamp(0.7 * cert + 0.3 * portfolio);
    }

    private boolean hasUrlsInRelatedExperiences(List<UserProfileExperienceEntity> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return false;
        }

        return experiences.stream()
                .anyMatch(exp -> exp != null && StringUtils.hasText(exp.getUrl()));
    }


    private List<RoleFitResponse.MissingSkill> buildMissingSkills(UserProfileEntity profile, TargetRoleEntity targetRole) {
        List<RoleFitResponse.MissingSkill> missing = new ArrayList<>();

        if (targetRole == null || targetRole.getRequiredSkills() == null || targetRole.getRequiredSkills().isEmpty()) {
            return missing;
        }

        List<String> ownedSkills = profile != null && profile.getTechStack() != null
                ? profile.getTechStack().stream()
                .map(skill -> skill.getName() != null ? skill.getName().toLowerCase(Locale.ROOT) : "")
                .toList()
                : Collections.emptyList();

        // 필수 스킬 중 사용자가 가지지 않은 것들을 찾기
        for (WeightedSkill required : targetRole.getRequiredSkills()) {
            if (required.getName() == null) {
                continue;
            }
            
            String skillName = required.getName().toLowerCase(Locale.ROOT);
            boolean hasSkill = ownedSkills.stream().anyMatch(s -> s.contains(skillName) || skillName.contains(s));
            
            if (!hasSkill) {
                double impact = required.getWeight() != null ? required.getWeight() * 0.1 : 0.05;
                missing.add(new RoleFitResponse.MissingSkill(required.getName(), clamp(impact)));
            }
        }

        // 영향도 순으로 정렬
        missing.sort(Comparator.comparing(RoleFitResponse.MissingSkill::impact).reversed());
        
        return missing;
    }

    private List<String> buildRecommendations(TargetRoleEntity targetRole) {
        if (targetRole == null) {
            return List.of(
                    "Contribute to open-source projects.",
                    "Run a study group in your field.",
                    "Get feedback via mentoring with experts in the field."
            );
        }

        List<String> recommendations = new ArrayList<>();
        String roleName = targetRole.getName() != null ? targetRole.getName() : "";
        String roleId = targetRole.getRoleId() != null ? targetRole.getRoleId().toLowerCase(Locale.ROOT) : "";

        // 역할별 맞춤 추천
        if (roleId.contains("backend") || roleName.toLowerCase(Locale.ROOT).contains("backend")) {
            recommendations.add("Build a side project with Spring Boot to gain system design experience.");
            recommendations.add("Prepare for AWS Certified Cloud Practitioner to validate cloud fundamentals.");
            if (targetRole.getRecommendedCerts() != null && !targetRole.getRecommendedCerts().isEmpty()) {
                recommendations.add("Consider obtaining: " + String.join(", ", targetRole.getRecommendedCerts()));
            }
        } else if (roleId.contains("data") || roleName.toLowerCase(Locale.ROOT).contains("data")) {
            recommendations.add("Join a public data analysis competition to gain hands-on experience.");
            recommendations.add("Prepare for TensorFlow certification and learn ML pipelines.");
            if (targetRole.getRecommendedCerts() != null && !targetRole.getRecommendedCerts().isEmpty()) {
                recommendations.add("Consider obtaining: " + String.join(", ", targetRole.getRecommendedCerts()));
            }
        } else {
            recommendations.add("Get feedback via mentoring with experts in the field.");
            recommendations.add("Contribute to open-source projects related to your target role.");
        }

        // 공통 추천
        if (recommendations.size() < 3) {
            recommendations.add("Build a portfolio showcasing your projects and achievements.");
        }

        return recommendations;
    }

    private List<ActivityEntity> fetchImprovementActivities(Long userId, int size) {
        int safeSize = size > 0 ? size : 5;
        try {
            return recommendService.getRecommendations(userId, safeSize, null, null);
        } catch (Exception ex) {
            return recommendService.getTrendingActivities(safeSize, null);
        }
    }

    private String buildImprovementReason(ActivityEntity activity, String affects) {
        String title = activity.getTitle() != null ? activity.getTitle() : "Activity";
        return "%s contributes to improving %s.".formatted(title, affects);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double roundScore(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String resolveTarget(String target) {
        return StringUtils.hasText(target) ? target : "general";
    }
}


