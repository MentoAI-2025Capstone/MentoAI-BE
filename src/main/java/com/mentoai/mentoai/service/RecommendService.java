package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityRecommendationResponse;
import com.mentoai.mentoai.controller.dto.ActivityResponse;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import com.mentoai.mentoai.repository.UserInterestRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendService {
    
    private final ActivityRepository activityRepository;
    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final RoleFitService roleFitService;
    private final TagRepository tagRepository;
    
    // 사용자 맞춤 활동 추천
    public List<ActivityEntity> getRecommendations(Long userId, Integer limit, String type, Boolean campusOnly) {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }
        
        // 사용자 관심사 조회
        List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userId);
        
        if (userInterests.isEmpty()) {
            // 관심사가 없으면 일반적인 인기 활동 추천
            return getTrendingActivities(limit, type);
        }
        
        // 관심사 기반 추천 로직
        List<ActivityEntity> recommendations = new ArrayList<>();
        
        // 1. 관심사 태그와 매칭되는 활동들 찾기
        List<Long> tagIds = userInterests.stream()
                .map(UserInterestEntity::getTagId)
                .collect(Collectors.toList());
        
        Pageable pageable = PageRequest.of(0, limit * 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // 태그 매칭 활동 조회
        List<ActivityEntity> tagMatchedActivities = activityRepository.findByFilters(
                null, // 검색어 없음
                type != null ? ActivityType.valueOf(type.toUpperCase()) : null,
                campusOnly,
                null, // 상태 필터 없음
                pageable
        ).getContent();
        
        // 태그 매칭 점수 계산
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        
        for (ActivityEntity activity : tagMatchedActivities) {
            double score = calculateActivityScore(activity, userInterests);
            if (score > 0) {
                activityScores.put(activity, score);
            }
        }
        
        // 점수 순으로 정렬하여 추천
        recommendations = activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // 추천이 부족하면 인기 활동으로 보완
        if (recommendations.size() < limit) {
            List<ActivityEntity> trending = getTrendingActivities(limit - recommendations.size(), type);
            recommendations.addAll(trending);
        }
        
        return recommendations;
    }
    
    // 활동 점수 계산 (관심사 기반)
    private double calculateActivityScore(ActivityEntity activity, List<UserInterestEntity> userInterests) {
        double score = 0.0;
        
        // 활동의 태그들과 사용자 관심사 매칭
        if (activity.getActivityTags() != null) {
            for (var activityTag : activity.getActivityTags()) {
                for (UserInterestEntity userInterest : userInterests) {
                    if (activityTag.getTag().getId().equals(userInterest.getTagId())) {
                        // 관심사 점수에 따라 가중치 적용
                        score += userInterest.getScore() * 0.3;
                    }
                }
            }
        }
        
        // 활동 유형 선호도 (간단한 규칙 기반)
        if (activity.getType() == ActivityType.STUDY) {
            score += 0.2;
        } else if (activity.getType() == ActivityType.CONTEST) {
            score += 0.1;
        }
        
        // 캠퍼스 활동 가중치
        if (activity.getIsCampus() != null && activity.getIsCampus()) {
            score += 0.1;
        }
        
        return score;
    }
    
    // 의미 기반 검색 (간단한 키워드 매칭)
    public List<ActivityEntity> semanticSearch(String query, Integer limit, String userId) {
        return semanticSearchWithScores(query, limit, userId).stream()
                .map(SemanticSearchResult::activity)
                .collect(Collectors.toList());
    }

    public List<SemanticSearchResult> semanticSearchWithScores(String query, Integer limit, String userId) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 필수입니다.");
        }

        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;
        
        // Gemini 임베딩 기반 검색 시도
        try {
            List<SemanticSearchResult> embeddingResults = semanticSearchWithEmbedding(query, safeLimit, userId);
            if (!embeddingResults.isEmpty()) {
                return embeddingResults;
            }
        } catch (Exception e) {
            log.warn("Gemini embedding search failed, falling back to keyword search", e);
        }
        
        // Fallback: 키워드 기반 검색
        List<String> searchTerms = expandSearchTerms(query);
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        
        for (String term : searchTerms) {
            Pageable pageable = PageRequest.of(0, safeLimit * 2, Sort.by(Sort.Direction.DESC, "createdAt"));
            
            List<ActivityEntity> results = activityRepository.findByFilters(
                    term.toLowerCase(),
                    null, // 타입 필터 없음
                    null, // 캠퍼스 필터 없음
                    null, // 상태 필터 없음
                    pageable
            ).getContent();
            
            // 각 활동에 대해 점수 계산
            for (ActivityEntity activity : results) {
                double score = calculateSearchScore(activity, term, searchTerms);
                activityScores.merge(activity, score, Double::sum);
            }
        }
        
        // 사용자 관심사 기반 가중치 적용
        if (userId != null && !userId.isEmpty()) {
            try {
                Long userIdLong = Long.valueOf(userId);
                List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userIdLong);
                
                if (!userInterests.isEmpty()) {
                    activityScores.replaceAll((activity, score) -> {
                        double interestScore = calculateActivityScore(activity, userInterests);
                        return score + (interestScore * 0.3); // 관심사 가중치 30%
                    });
                }
            } catch (NumberFormatException e) {
                // userId가 잘못된 형식이면 무시
            }
        }
        
        // 점수 순으로 정렬하여 반환
        return activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(safeLimit)
                .map(entry -> new SemanticSearchResult(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    
    // Gemini 임베딩 기반 의미 검색
    private List<SemanticSearchResult> semanticSearchWithEmbedding(String query, int limit, String userId) {
        // 검색어 임베딩 생성
        List<Double> queryEmbedding = geminiService.generateEmbedding(query);
        
        // 활동 목록 조회 (최근 활동 위주)
        Pageable pageable = PageRequest.of(0, limit * 3, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ActivityEntity> activities = activityRepository.findByFilters(
                null, null, null, null, pageable
        ).getContent();
        
        // 각 활동의 텍스트를 임베딩으로 변환하고 유사도 계산
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        
        for (ActivityEntity activity : activities) {
            try {
                String activityText = buildActivityText(activity);
                List<Double> activityEmbedding = geminiService.generateEmbedding(activityText);
                
                double similarity = geminiService.cosineSimilarity(queryEmbedding, activityEmbedding);
                
                if (similarity > 0.3) { // 최소 유사도 임계값
                    activityScores.put(activity, similarity * 100); // 0-100 점수로 변환
                }
            } catch (Exception e) {
                log.warn("Failed to generate embedding for activity {}: {}", activity.getId(), e.getMessage());
            }
        }
        
        // 사용자 관심사 기반 가중치 적용
        if (userId != null && !userId.isEmpty()) {
            try {
                Long userIdLong = Long.valueOf(userId);
                List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userIdLong);
                
                if (!userInterests.isEmpty()) {
                    activityScores.replaceAll((activity, score) -> {
                        double interestScore = calculateActivityScore(activity, userInterests);
                        return score * 0.7 + (interestScore * 30); // 임베딩 70%, 관심사 30%
                    });
                }
            } catch (NumberFormatException e) {
                // userId가 잘못된 형식이면 무시
            }
        }
        
        // 점수 순으로 정렬하여 반환
        return activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new SemanticSearchResult(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    
    // 활동의 제목, 내용, 태그를 결합하여 텍스트 생성
    private String buildActivityText(ActivityEntity activity) {
        StringBuilder text = new StringBuilder();
        
        if (activity.getTitle() != null) {
            text.append(activity.getTitle()).append(" ");
        }
        
        if (activity.getSummary() != null) {
            text.append(activity.getSummary()).append(" ");
        }
        
        if (activity.getContent() != null) {
            // 내용이 너무 길면 앞부분만 사용
            String content = activity.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            text.append(content).append(" ");
        }
        
        // 태그 추가
        if (activity.getActivityTags() != null) {
            for (var activityTag : activity.getActivityTags()) {
                if (activityTag.getTag() != null && activityTag.getTag().getName() != null) {
                    text.append(activityTag.getTag().getName()).append(" ");
                }
            }
        }
        
        return text.toString().trim();
    }
    
    // 검색어 확장 (동의어, 관련어 추가)
    private List<String> expandSearchTerms(String query) {
        List<String> terms = new ArrayList<>();
        terms.add(query); // 원본 검색어
        
        // 간단한 동의어 매핑
        Map<String, List<String>> synonyms = Map.of(
            "개발", List.of("프로그래밍", "코딩", "소프트웨어"),
            "디자인", List.of("UI", "UX", "그래픽"),
            "마케팅", List.of("홍보", "광고", "브랜딩"),
            "스터디", List.of("공부", "학습", "연구"),
            "취업", List.of("채용", "구직", "인턴"),
            "창업", List.of("스타트업", "사업", "비즈니스")
        );
        
        String lowerQuery = query.toLowerCase();
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                terms.addAll(entry.getValue());
            }
            for (String synonym : entry.getValue()) {
                if (lowerQuery.contains(synonym)) {
                    terms.add(entry.getKey());
                    terms.addAll(entry.getValue());
                }
            }
        }
        
        return terms.stream().distinct().collect(Collectors.toList());
    }
    
    // 검색 점수 계산
    private double calculateSearchScore(ActivityEntity activity, String term, List<String> allTerms) {
        double score = 0.0;
        String title = activity.getTitle().toLowerCase();
        String content = activity.getContent() != null ? activity.getContent().toLowerCase() : "";
        
        // 제목에서 매칭 (가중치 높음)
        if (title.contains(term.toLowerCase())) {
            score += 2.0;
        }
        
        // 내용에서 매칭
        if (content.contains(term.toLowerCase())) {
            score += 1.0;
        }
        
        // 정확한 매칭 보너스
        if (title.equals(term.toLowerCase())) {
            score += 3.0;
        }
        
        // 태그 매칭 (활동 태그가 있다면)
        if (activity.getActivityTags() != null) {
            for (var activityTag : activity.getActivityTags()) {
                String tagName = activityTag.getTag().getName().toLowerCase();
                if (tagName.contains(term.toLowerCase())) {
                    score += 1.5;
                }
            }
        }
        
        return score;
    }
    
    // 인기 활동 조회
    public List<ActivityEntity> getTrendingActivities(Integer limit, String type) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        ActivityType activityType = null;
        if (type != null && !type.isEmpty()) {
            try {
                activityType = ActivityType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 잘못된 타입이면 무시
            }
        }
        
        return activityRepository.findByFilters(
                null, // 검색어 없음
                activityType,
                null, // 캠퍼스 필터 없음
                null, // 상태 필터 없음
                pageable
        ).getContent();
    }
    
    // 유사 활동 추천
    public List<ActivityEntity> getSimilarActivities(Long activityId, Integer limit) {
        Optional<ActivityEntity> targetActivity = activityRepository.findById(activityId);
        if (targetActivity.isEmpty()) {
            throw new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId);
        }
        
        ActivityEntity activity = targetActivity.get();
        
        // 같은 유형의 활동들 중에서 추천
        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<ActivityEntity> similarActivities = activityRepository.findByFilters(
                null, // 검색어 없음
                activity.getType(),
                activity.getIsCampus(),
                null, // 상태 필터 없음
                pageable
        ).getContent();
        
        // 자기 자신 제외
        return similarActivities.stream()
                .filter(a -> !a.getId().equals(activityId))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public record SemanticSearchResult(ActivityEntity activity, double score) {
    }
    
    // 점수 포함 활동 추천
    public List<ActivityRecommendationResponse> getRecommendationsWithScores(
            Long userId, Integer limit, String type, Boolean campusOnly, String targetRole) {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }
        
        // 기본 추천 활동 조회
        List<ActivityEntity> activities = getRecommendations(userId, limit * 2, type, campusOnly);
        
        // 사용자 관심사 조회
        List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userId);
        
        // RoleFitScore 계산 (타겟 직무가 있는 경우)
        Double roleFitScore = null;
        if (targetRole != null && !targetRole.trim().isEmpty()) {
            try {
                var roleFitResponse = roleFitService.calculateRoleFit(userId, new RoleFitRequest(targetRole, null));
                roleFitScore = roleFitResponse.roleFitScore();
            } catch (Exception e) {
                log.warn("Failed to calculate role fit score for user {} and role {}: {}", userId, targetRole, e.getMessage());
            }
        }
        
        // 각 활동에 대해 점수 계산
        Map<ActivityEntity, ActivityRecommendationResponse> scoredActivities = new HashMap<>();
        
        for (ActivityEntity activity : activities) {
            try {
                // 1. 관심사 기반 점수 (0-100)
                double interestScore = calculateActivityScore(activity, userInterests) * 100;
                
                // 2. Gemini 임베딩 기반 점수 (0-100) - 활동 텍스트 기반
                double embeddingScore = 0.0;
                try {
                    String activityText = buildActivityText(activity);
                    List<Double> activityEmbedding = geminiService.generateEmbedding(activityText);
                    
                    // 사용자 프로필 기반 검색어 생성 (간단한 키워드 추출)
                    String userQuery = buildUserQuery(userId, targetRole);
                    if (userQuery != null && !userQuery.trim().isEmpty()) {
                        List<Double> queryEmbedding = geminiService.generateEmbedding(userQuery);
                        double similarity = geminiService.cosineSimilarity(queryEmbedding, activityEmbedding);
                        embeddingScore = similarity * 100;
                    }
                } catch (Exception e) {
                    log.debug("Failed to calculate embedding score for activity {}: {}", activity.getId(), e.getMessage());
                }
                
                // 3. 최종 추천 점수 계산
                // 공식: 0.5 * 임베딩 점수 + 0.3 * RoleFitScore + 0.2 * 관심사 점수
                double recommendationScore;
                if (roleFitScore != null) {
                    recommendationScore = 0.5 * embeddingScore + 0.3 * roleFitScore + 0.2 * interestScore;
                } else {
                    recommendationScore = 0.7 * embeddingScore + 0.3 * interestScore;
                }
                
                // 4. 예상 점수 증가량 계산
                Double expectedScoreIncrease = calculateExpectedScoreIncrease(activity, userId, targetRole);
                
                ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
                scoredActivities.put(activity, new ActivityRecommendationResponse(
                        activityResponse,
                        Math.round(recommendationScore * 10.0) / 10.0, // 소수점 1자리
                        roleFitScore,
                        expectedScoreIncrease
                ));
            } catch (Exception e) {
                log.warn("Failed to calculate score for activity {}: {}", activity.getId(), e.getMessage());
            }
        }
        
        // 점수 순으로 정렬하여 반환
        return scoredActivities.values().stream()
                .sorted(Comparator.comparing(ActivityRecommendationResponse::recommendationScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    // 사용자 쿼리 생성 (프로필 기반)
    private String buildUserQuery(Long userId, String targetRole) {
        StringBuilder query = new StringBuilder();
        
        if (targetRole != null && !targetRole.trim().isEmpty()) {
            query.append(targetRole).append(" ");
        }
        
        // 사용자 관심사 태그 추가
        List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userId);
        if (!userInterests.isEmpty()) {
            for (UserInterestEntity interest : userInterests) {
                tagRepository.findById(interest.getTagId()).ifPresent(tag -> {
                    if (tag.getName() != null) {
                        query.append(tag.getName()).append(" ");
                    }
                });
            }
        }
        
        return query.toString().trim();
    }
    
    // 활동 완료 시 예상 점수 증가량 계산
    private Double calculateExpectedScoreIncrease(ActivityEntity activity, Long userId, String targetRole) {
        if (targetRole == null || targetRole.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 활동 유형에 따른 점수 증가량 추정
            double baseIncrease = 0.0;
            if (activity.getType() == ActivityType.CONTEST) {
                baseIncrease = 3.0; // 공모전 완료 시 평균 3점 증가
            } else if (activity.getType() == ActivityType.STUDY) {
                baseIncrease = 2.0; // 스터디 완료 시 평균 2점 증가
            } else if (activity.getType() == ActivityType.JOB) {
                baseIncrease = 1.5; // 취업 활동 완료 시 평균 1.5점 증가
            } else {
                baseIncrease = 1.0; // 기타 활동 완료 시 평균 1점 증가
            }
            
            // 활동의 태그와 타겟 직무의 스킬 매칭도에 따라 가중치 적용
            // 간단한 추정: 활동이 타겟 직무와 관련이 있으면 추가 증가
            if (activity.getActivityTags() != null && !activity.getActivityTags().isEmpty()) {
                // 태그가 있으면 약간의 보너스
                baseIncrease *= 1.1;
            }
            
            return Math.round(baseIncrease * 10.0) / 10.0; // 소수점 1자리
        } catch (Exception e) {
            log.debug("Failed to calculate expected score increase: {}", e.getMessage());
            return null;
        }
    }
}
