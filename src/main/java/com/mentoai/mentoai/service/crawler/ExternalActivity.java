package com.mentoai.mentoai.service.crawler;

/**
 * 외부 사이트에서 크롤링한 활동 데이터를 나타내는 DTO
 */
public record ExternalActivity(
        String source,           // 크롤러 소스 이름 (예: "linkareer", "sitex")
        String title,             // 활동 제목
        String externalId,       // 사이트별 고유 ID
        Long recruitCloseAt,     // 모집 마감일 (밀리초 타임스탬프)
        String organizationName,  // 주최 기관명
        String field,            // 분야/카테고리
        String url               // 전체 URL
) {
    /**
     * URL이 없을 경우 기본 URL 생성
     */
    public String getUrlOrDefault() {
        if (url != null && !url.trim().isEmpty()) {
            return url;
        }
        // 기본 URL 패턴 (각 크롤러에서 오버라이드 가능)
        return switch (source.toLowerCase()) {
            case "linkareer" -> externalId != null 
                    ? "https://linkareer.com/activity/" + externalId 
                    : null;
            default -> null;
        };
    }
}

