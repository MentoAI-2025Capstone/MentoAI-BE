package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityRecommendationResponse;
import com.mentoai.mentoai.controller.dto.ActivityResponse;
import com.mentoai.mentoai.controller.dto.RecommendRequest;
import com.mentoai.mentoai.controller.dto.RecommendResponse;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
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
    private final UserProfileService userProfileService;
    private final UserInterestService userInterestService;
    private final TargetRoleService targetRoleService;
    
    // 사용자 맞춤 활동 추천
    public List<ActivityEntity> getRecommendations(Long userId, Integer limit, String type, Boolean campusOnly) {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }
        
        // 사용자 관심사 조회
        List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userId);
        
        if (userInterests.isEmpty()) {
            // 관심사가 없으면 빈 리스트 반환 (모든 활동 반환 X)
            log.warn("User {} has no interests, returning empty recommendations", userId);
            return List.of();
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
        
        // 추천이 부족해도 인기 활동으로 보완하지 않음 (사용자 맞춤만 반환)
        return recommendations;
    }
    
    // 활동 점수 계산 (관심사 기반)
    private double calculateActivityScore(ActivityEntity activity, List<UserInterestEntity> userInterests) {
        double score = 0.0;
        
        // 활동의 태그들과 사용자 관심사 매칭
        if (activity.getActivityTags() != null && !activity.getActivityTags().isEmpty()) {
            for (var activityTag : activity.getActivityTags()) {
                for (UserInterestEntity userInterest : userInterests) {
                    if (activityTag.getTag().getId().equals(userInterest.getTagId())) {
                        // 관심사 점수(1-5)를 0-50점 범위로 변환 (10배 증가)
                        score += userInterest.getScore() * 10.0;
                    }
                }
            }
        }
        
        // 활동 유형 선호도 (간단한 규칙 기반)
        if (activity.getType() == ActivityType.STUDY) {
            score += 5.0;  // 0.2 -> 5.0
        } else if (activity.getType() == ActivityType.CONTEST) {
            score += 3.0;  // 0.1 -> 3.0
        }
        
        // 캠퍼스 활동 가중치
        if (activity.getIsCampus() != null && activity.getIsCampus()) {
            score += 2.0;  // 0.1 -> 2.0
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
    
    /**
     * RAG 기반 맞춤 추천 (사용자 프롬프트 기반)
     */
    public RecommendResponse getRecommendationsByRequest(RecommendRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        
        if (!userRepository.existsById(request.userId())) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.userId());
        }
        
        // 1. 사용자 프로필 및 관심사 수집
        UserProfileResponse userProfile = userProfileService.getProfile(request.userId());
        List<UserInterestEntity> userInterests = userInterestService.getUserInterests(request.userId());
        
        // 2. 관련 활동 검색 (Retrieval)
        List<ActivityEntity> candidateActivities = retrieveRelevantActivities(
                request, userProfile, userInterests, request.getTopKOrDefault() * 2
        );
        
        // 추가 fallback: 여전히 비어있으면 사용자 관심사 기반으로 재시도
        if (candidateActivities.isEmpty() && !userInterests.isEmpty()) {
            log.warn("No candidate activities found after retrieval, trying user interest-based search");
            // 사용자 관심사 태그로 필터링된 활동만 반환
            List<String> interestTagNames = userInterests.stream()
                    .map(interest -> tagRepository.findById(interest.getTagId())
                            .map(tag -> tag.getName())
                            .orElse(null))
                    .filter(name -> name != null)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (!interestTagNames.isEmpty()) {
                Pageable pageable = PageRequest.of(0, request.getTopKOrDefault() * 2, 
                        Sort.by(Sort.Direction.DESC, "createdAt"));
                candidateActivities = activityRepository.findByComplexFilters(
                        null, null, null, null,
                        interestTagNames,
                        pageable
                ).getContent();
            }
            
            // 여전히 비어있으면 빈 응답 반환 (사용자 맞춤 활동이 없음)
            if (candidateActivities.isEmpty()) {
                log.warn("No personalized activities found for user {}", request.userId());
                return new RecommendResponse(List.of());
            }
        } else if (candidateActivities.isEmpty()) {
            // 관심사도 없고 검색 결과도 없으면 빈 응답 반환
            log.warn("No activities found and user has no interests");
            return new RecommendResponse(List.of());
        }
        
        // 3. Gemini에 RAG 프롬프트 구성 및 전송
        String prompt = buildRAGPrompt(request, userProfile, userInterests, candidateActivities);
        String geminiResponse;
        try {
            geminiResponse = geminiService.generateText(prompt);
        } catch (Exception e) {
            log.error("Failed to generate recommendation from Gemini API", e);
            // Fallback: 점수 기반 추천
            return fallbackToScoreBasedRecommendation(request, candidateActivities);
        }
        
        // 4. Gemini 응답 파싱하여 구조화된 결과 반환
        List<RecommendResponse.RecommendItem> items = parseGeminiRecommendationResponse(
                geminiResponse, candidateActivities, request.getTopKOrDefault()
        );
        
        return new RecommendResponse(items);
    }
    
    /**
     * 관련 활동 검색 (Retrieval)
     */
    private List<ActivityEntity> retrieveRelevantActivities(
            RecommendRequest request,
            UserProfileResponse userProfile,
            List<UserInterestEntity> userInterests,
            int limit) {
        
        List<ActivityEntity> activities = new ArrayList<>();
        
        // query가 있으면 의미 기반 검색
        if (request.query() != null && !request.query().trim().isEmpty()) {
            List<SemanticSearchResult> searchResults = semanticSearchWithScores(
                    request.query(),
                    limit,
                    request.userId().toString()
            );
            activities.addAll(searchResults.stream()
                    .map(SemanticSearchResult::activity)
                    .toList());
        }
        
        // preferTags가 있으면 태그 기반 검색
        if (request.preferTags() != null && !request.preferTags().isEmpty()) {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
            // 태그 이름으로 활동 검색 (간단한 구현)
            for (String tagName : request.preferTags()) {
                List<ActivityEntity> tagActivities = activityRepository.findByFilters(
                        tagName,
                        null,
                        null,
                        null,
                        pageable
                ).getContent();
                activities.addAll(tagActivities);
            }
        }
        
        // 사용자 관심사 기반 검색
        if (!userInterests.isEmpty() && request.getUseProfileHintsOrDefault()) {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<ActivityEntity> interestActivities = getRecommendations(
                    request.userId(),
                    limit,
                    null,
                    null
            );
            activities.addAll(interestActivities);
        }
        
        // 중복 제거 및 정렬
        activities = activities.stream()
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
        
        // 결과가 없으면 사용자 관심사 기반 추천으로 fallback (일반 활동 목록 X)
        if (activities.isEmpty() && !userInterests.isEmpty()) {
            log.warn("No relevant activities found, using user interest-based recommendations");
            // 사용자 관심사 태그로 필터링된 활동만 반환
            List<String> interestTagNames = userInterests.stream()
                    .map(interest -> tagRepository.findById(interest.getTagId())
                            .map(tag -> tag.getName())
                            .orElse(null))
                    .filter(name -> name != null)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (!interestTagNames.isEmpty()) {
                Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
                activities = activityRepository.findByComplexFilters(
                        null, null, null, null,
                        interestTagNames,
                        pageable
                ).getContent();
            }
        }
        
        return activities;
    }
    
    /**
     * RAG 프롬프트 구성
     */
    private String buildRAGPrompt(
            RecommendRequest request,
            UserProfileResponse userProfile,
            List<UserInterestEntity> userInterests,
            List<ActivityEntity> activities) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 대학생 진로 상담 전문가입니다. 사용자의 프로필과 질의를 바탕으로 활동을 추천해주세요.\n\n");
        
        // 사용자 프로필 정보
        prompt.append("=== 사용자 프로필 ===\n");
        
        if (userProfile.university() != null) {
            prompt.append(String.format("대학: %s, 학년: %d학년, 전공: %s\n",
                    userProfile.university().universityName() != null ? userProfile.university().universityName() : "미입력",
                    userProfile.university().grade() != null ? userProfile.university().grade() : 0,
                    userProfile.university().major() != null ? userProfile.university().major() : "미입력"));
        }
        
        // 관심 분야
        if (userProfile.interestDomains() != null && !userProfile.interestDomains().isEmpty()) {
            prompt.append("관심 분야: ").append(String.join(", ", userProfile.interestDomains())).append("\n");
        }
        
        // 관심 태그
        if (!userInterests.isEmpty()) {
            List<String> interestTags = userInterests.stream()
                    .map(interest -> tagRepository.findById(interest.getTagId())
                            .map(tag -> tag.getName())
                            .orElse(""))
                    .filter(name -> !name.isEmpty())
                    .toList();
            if (!interestTags.isEmpty()) {
                prompt.append("관심 태그: ").append(String.join(", ", interestTags)).append("\n");
            }
        }
        
        // 기술 스택
        if (userProfile.techStack() != null && !userProfile.techStack().isEmpty()) {
            List<String> skills = userProfile.techStack().stream()
                    .map(skill -> skill.name() + (skill.level() != null ? " (" + skill.level() + ")" : ""))
                    .toList();
            prompt.append("기술 스택: ").append(String.join(", ", skills)).append("\n");
        }
        
        // 경험
        if (userProfile.experiences() != null && !userProfile.experiences().isEmpty()) {
            prompt.append("주요 경험:\n");
            for (UserProfileResponse.Experience exp : userProfile.experiences()) {
                prompt.append(String.format("- %s: %s (%s)\n",
                        exp.type(), exp.title(), exp.organization()));
            }
        }
        
        // 자연어 질의
        prompt.append("\n=== 사용자 질의 ===\n");
        prompt.append(request.query() != null ? request.query() : "활동 추천을 요청합니다.").append("\n");
        
        // 선호 태그
        if (request.preferTags() != null && !request.preferTags().isEmpty()) {
            prompt.append("선호 태그: ").append(String.join(", ", request.preferTags())).append("\n");
        }
        
        // 후보 활동 목록
        prompt.append("\n=== 후보 활동 목록 ===\n");
        for (int i = 0; i < Math.min(activities.size(), 20); i++) {
            ActivityEntity activity = activities.get(i);
            prompt.append(String.format("[%d] %s\n", i + 1, activity.getTitle()));
            if (activity.getSummary() != null) {
                prompt.append("   요약: ").append(activity.getSummary()).append("\n");
            }
            if (activity.getActivityTags() != null && !activity.getActivityTags().isEmpty()) {
                List<String> tagNames = activity.getActivityTags().stream()
                        .map(at -> at.getTag().getName())
                        .toList();
                prompt.append("   태그: ").append(String.join(", ", tagNames)).append("\n");
            }
            prompt.append("\n");
        }
        
        // 추천 요청
        prompt.append("\n=== 요청사항 ===\n");
        prompt.append(String.format("위 정보를 바탕으로 사용자에게 가장 적합한 활동 %d개를 추천하고, ", request.getTopKOrDefault()));
        prompt.append("각 추천에 대해 구체적인 이유를 설명해주세요.\n");
        prompt.append("JSON 형식으로 반환해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"items\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"activityIndex\": 1,\n");
        prompt.append("      \"score\": 85.5,\n");
        prompt.append("      \"reason\": \"이 공모전은 백엔드 개발 경험을 쌓기에 적합합니다. 사용자의 Spring Boot 경험과 연계하여 실무 역량을 향상시킬 수 있습니다.\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("activityIndex는 위 후보 활동 목록의 번호(1부터 시작)입니다.\n");
        prompt.append("score는 0-100 사이의 추천 점수입니다.\n");
        prompt.append("reason은 사용자가 이해하기 쉬운 자연어로 작성해주세요.");
        
        return prompt.toString();
    }
    
    /**
     * Gemini 응답 파싱
     */
    private List<RecommendResponse.RecommendItem> parseGeminiRecommendationResponse(
            String geminiResponse,
            List<ActivityEntity> candidateActivities,
            int topK) {
        
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        
        try {
            // JSON 파싱 시도
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(geminiResponse);
            
            com.fasterxml.jackson.databind.JsonNode itemsNode = jsonNode.path("items");
            if (itemsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode itemNode : itemsNode) {
                    int activityIndex = itemNode.path("activityIndex").asInt() - 1; // 1-based to 0-based
                    double score = itemNode.path("score").asDouble();
                    String reason = itemNode.path("reason").asText();
                    
                    if (activityIndex >= 0 && activityIndex < candidateActivities.size()) {
                        ActivityEntity activity = candidateActivities.get(activityIndex);
                        ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
                        items.add(new RecommendResponse.RecommendItem(
                                activityResponse,
                                score,
                                reason
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response, trying text parsing", e);
            // JSON 파싱 실패 시 텍스트 기반 파싱 시도
            items = parseGeminiTextResponse(geminiResponse, candidateActivities, topK);
        }
        
        // 최대 topK개로 제한
        return items.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }
    
    /**
     * Gemini 텍스트 응답 파싱 (Fallback)
     */
    private List<RecommendResponse.RecommendItem> parseGeminiTextResponse(
            String geminiResponse,
            List<ActivityEntity> candidateActivities,
            int topK) {
        
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        
        // 간단한 텍스트 파싱 (활동 제목 매칭)
        for (ActivityEntity activity : candidateActivities.stream().limit(topK).toList()) {
            if (geminiResponse.contains(activity.getTitle())) {
                ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
                items.add(new RecommendResponse.RecommendItem(
                        activityResponse,
                        75.0, // 기본 점수
                        "사용자의 프로필과 질의를 기반으로 추천되었습니다."
                ));
            }
        }
        
        return items;
    }
    
    /**
     * Fallback: 점수 기반 추천
     */
    private RecommendResponse fallbackToScoreBasedRecommendation(
            RecommendRequest request,
            List<ActivityEntity> candidateActivities) {
        
        // preferTags에서 targetRole 추출 시도
        String targetRole = null;
        if (request.preferTags() != null && !request.preferTags().isEmpty()) {
            targetRole = request.preferTags().get(0);
        }
        
        List<ActivityRecommendationResponse> scored = getRecommendationsWithScores(
                request.userId(),
                request.getTopKOrDefault(),
                null,
                null,
                targetRole
        );
        
        List<RecommendResponse.RecommendItem> items = scored.stream()
                .map(rec -> new RecommendResponse.RecommendItem(
                        rec.activity(),
                        rec.recommendationScore(),
                        generateSimpleReason(rec)
                ))
                .collect(Collectors.toList());
        
        return new RecommendResponse(items);
    }
    
    /**
     * 간단한 추천 이유 생성
     */
    private String generateSimpleReason(ActivityRecommendationResponse rec) {
        StringBuilder reason = new StringBuilder();
        if (rec.roleFitScore() != null) {
            reason.append(String.format("직무 적합도: %.1f점. ", rec.roleFitScore()));
        }
        reason.append("사용자의 관심사와 프로필을 기반으로 추천되었습니다.");
        return reason.toString();
    }
}
