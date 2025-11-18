package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.ActivityRecommendationResponse;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recommend")
@Tag(name = "recommend", description = "활동 추천")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @GetMapping("/activities/{userId}")
    @Operation(summary = "사용자 맞춤 활동 추천", description = "사용자의 관심사와 프로필을 기반으로 활동을 추천합니다.")
    public ResponseEntity<List<ActivityEntity>> getRecommendations(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @Parameter(description = "추천 개수") @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "활동 유형") @RequestParam(required = false) String type,
            @Parameter(description = "캠퍼스 활동만") @RequestParam(required = false) Boolean campusOnly) {
        try {
            List<ActivityEntity> recommendations = recommendService.getRecommendations(
                    userId, limit, type, campusOnly);
            return ResponseEntity.ok(recommendations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/activities/{userId}/with-scores")
    @Operation(summary = "점수 포함 활동 추천", description = "사용자의 관심사, 프로필, 직무 적합도를 기반으로 활동을 추천하고 점수를 포함하여 반환합니다.")
    public ResponseEntity<List<ActivityRecommendationResponse>> getRecommendationsWithScores(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @Parameter(description = "추천 개수") @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "활동 유형") @RequestParam(required = false) String type,
            @Parameter(description = "캠퍼스 활동만") @RequestParam(required = false) Boolean campusOnly,
            @Parameter(description = "타겟 직무 (예: 백엔드, 프론트엔드)") @RequestParam(required = false) String targetRole) {
        try {
            List<ActivityRecommendationResponse> recommendations = recommendService.getRecommendationsWithScores(
                    userId, limit, type, campusOnly, targetRole);
            return ResponseEntity.ok(recommendations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/semantic-search")
    @Operation(summary = "의미 기반 검색", description = "자연어 쿼리를 기반으로 활동을 검색합니다.")
    public ResponseEntity<List<ActivityEntity>> semanticSearch(
            @RequestBody Map<String, Object> searchRequest) {
        try {
            String query = (String) searchRequest.get("query");
            Integer limit = (Integer) searchRequest.getOrDefault("limit", 10);
            String userId = (String) searchRequest.get("userId");
            
            List<ActivityEntity> results = recommendService.semanticSearch(query, limit, userId);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/trending")
    @Operation(summary = "인기 활동 조회", description = "현재 인기 있는 활동들을 조회합니다.")
    public ResponseEntity<List<ActivityEntity>> getTrendingActivities(
            @Parameter(description = "조회 개수") @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "활동 유형") @RequestParam(required = false) String type) {
        try {
            List<ActivityEntity> trending = recommendService.getTrendingActivities(limit, type);
            return ResponseEntity.ok(trending);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/similar/{activityId}")
    @Operation(summary = "유사 활동 추천", description = "특정 활동과 유사한 활동들을 추천합니다.")
    public ResponseEntity<List<ActivityEntity>> getSimilarActivities(
            @Parameter(description = "활동 ID") @PathVariable Long activityId,
            @Parameter(description = "추천 개수") @RequestParam(defaultValue = "5") Integer limit) {
        try {
            List<ActivityEntity> similar = recommendService.getSimilarActivities(activityId, limit);
            return ResponseEntity.ok(similar);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}