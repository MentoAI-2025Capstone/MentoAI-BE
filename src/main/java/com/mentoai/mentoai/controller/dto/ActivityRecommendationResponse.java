package com.mentoai.mentoai.controller.dto;

public record ActivityRecommendationResponse(
        ActivityResponse activity,
        Double recommendationScore,  // 추천 점수 (0-100)
        Double roleFitScore,        // 직무 적합도 점수 (0-100, null 가능)
        Double expectedScoreIncrease // 활동 완료 시 예상 점수 증가량 (null 가능)
) {
}

