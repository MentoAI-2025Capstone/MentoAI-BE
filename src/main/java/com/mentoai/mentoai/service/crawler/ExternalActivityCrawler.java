package com.mentoai.mentoai.service.crawler;

import java.util.List;

/**
 * 외부 사이트에서 활동 데이터를 크롤링하는 인터페이스
 */
public interface ExternalActivityCrawler {
    
    /**
     * 크롤러 소스 이름 반환 (예: "linkareer", "sitex")
     */
    String getSourceName();
    
    /**
     * 전체 활동 크롤링
     * @return 크롤링된 활동 목록
     */
    List<ExternalActivity> crawlAll();
    
    /**
     * 최신 활동 일부 크롤링
     * @return 크롤링된 활동 목록
     */
    List<ExternalActivity> crawlRecent();
}

