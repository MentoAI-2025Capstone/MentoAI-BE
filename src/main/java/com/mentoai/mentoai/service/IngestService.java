package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class IngestService {
    
    private final ActivityRepository activityRepository;
    private final TagRepository tagRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // 데이터 수집 트리거
    @Transactional
    public Map<String, Object> triggerIngest(String source, Map<String, Object> config) {
        log.info("데이터 수집 시작: source={}, config={}", source, config);
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", LocalDateTime.now());
        result.put("source", source);
        
        // 비동기로 데이터 수집 실행
        CompletableFuture.runAsync(() -> {
            try {
                performDataIngest(source, config);
            } catch (Exception e) {
                log.error("데이터 수집 중 오류 발생", e);
            }
        }, executorService);
        
        return result;
    }
    
    // 실제 데이터 수집 수행
    private void performDataIngest(String source, Map<String, Object> config) {
        try {
            switch (source.toLowerCase()) {
                case "campus":
                    ingestCampusActivities(config);
                    break;
                case "external":
                    ingestExternalActivities(config);
                    break;
                case "manual":
                    ingestManualActivities(config);
                    break;
                default:
                    log.warn("알 수 없는 수집 소스: {}", source);
            }
        } catch (Exception e) {
            log.error("데이터 수집 실패: source={}", source, e);
        }
    }
    
    // 교내 활동 수집
    private void ingestCampusActivities(Map<String, Object> config) {
        log.info("교내 활동 수집 시작");
        
        // 실제로는 학교 홈페이지나 공지사항을 크롤링
        List<Map<String, Object>> campusActivities = generateSampleCampusActivities();
        
        int created = 0;
        for (Map<String, Object> activityData : campusActivities) {
            try {
                ActivityEntity activity = createActivityFromData(activityData);
                activity.setIsCampus(true);
                activity.setType(ActivityEntity.ActivityType.CAMPUS);
                
                activityRepository.save(activity);
                created++;
                
                log.debug("교내 활동 생성: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("교내 활동 생성 실패: {}", activityData, e);
            }
        }
        
        log.info("교내 활동 수집 완료: {}개 생성", created);
    }
    
    // 외부 활동 수집
    private void ingestExternalActivities(Map<String, Object> config) {
        log.info("외부 활동 수집 시작");
        
        // 실제로는 외부 사이트들을 크롤링
        List<Map<String, Object>> externalActivities = generateSampleExternalActivities();
        
        int created = 0;
        for (Map<String, Object> activityData : externalActivities) {
            try {
                ActivityEntity activity = createActivityFromData(activityData);
                activity.setIsCampus(false);
                
                activityRepository.save(activity);
                created++;
                
                log.debug("외부 활동 생성: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("외부 활동 생성 실패: {}", activityData, e);
            }
        }
        
        log.info("외부 활동 수집 완료: {}개 생성", created);
    }
    
    // 수동 입력 활동 수집
    private void ingestManualActivities(Map<String, Object> config) {
        log.info("수동 입력 활동 수집 시작");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> manualActivities = (List<Map<String, Object>>) config.get("activities");
        
        if (manualActivities == null || manualActivities.isEmpty()) {
            log.warn("수동 입력 활동 데이터가 없습니다");
            return;
        }
        
        int created = 0;
        for (Map<String, Object> activityData : manualActivities) {
            try {
                ActivityEntity activity = createActivityFromData(activityData);
                activityRepository.save(activity);
                created++;
                
                log.debug("수동 활동 생성: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("수동 활동 생성 실패: {}", activityData, e);
            }
        }
        
        log.info("수동 입력 활동 수집 완료: {}개 생성", created);
    }
    
    // 데이터로부터 활동 엔티티 생성
    private ActivityEntity createActivityFromData(Map<String, Object> data) {
        ActivityEntity activity = new ActivityEntity();
        
        activity.setTitle((String) data.get("title"));
        activity.setContent((String) data.get("content"));
        activity.setOrganizer((String) data.get("organizer"));
        activity.setUrl((String) data.get("url"));
        
        // 타입 설정
        String typeStr = (String) data.get("type");
        if (typeStr != null) {
            try {
                activity.setType(ActivityEntity.ActivityType.valueOf(typeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                activity.setType(ActivityEntity.ActivityType.STUDY); // 기본값
            }
        } else {
            activity.setType(ActivityEntity.ActivityType.STUDY);
        }
        
        // 상태 설정
        activity.setStatus(ActivityEntity.ActivityStatus.ACTIVE);
        
        // 날짜 설정
        String startDateStr = (String) data.get("startDate");
        String endDateStr = (String) data.get("endDate");
        
        if (startDateStr != null) {
            activity.setStartDate(LocalDateTime.parse(startDateStr));
        }
        if (endDateStr != null) {
            activity.setEndDate(LocalDateTime.parse(endDateStr));
        }
        
        return activity;
    }
    
    // 샘플 교내 활동 데이터 생성
    private List<Map<String, Object>> generateSampleCampusActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        
        activities.add(Map.of(
            "title", "2024년 하반기 학과별 프로젝트 발표회",
            "content", "컴퓨터공학과 학생들의 프로젝트 발표회가 개최됩니다.",
            "organizer", "컴퓨터공학과",
            "type", "STUDY",
            "startDate", "2024-11-15T14:00:00",
            "endDate", "2024-11-15T17:00:00"
        ));
        
        activities.add(Map.of(
            "title", "창업 동아리 신규 멤버 모집",
            "content", "창업에 관심 있는 학생들을 위한 동아리입니다.",
            "organizer", "창업지원센터",
            "type", "CAMPUS",
            "startDate", "2024-11-20T18:00:00",
            "endDate", "2024-11-20T20:00:00"
        ));
        
        return activities;
    }
    
    // 샘플 외부 활동 데이터 생성
    private List<Map<String, Object>> generateSampleExternalActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        
        activities.add(Map.of(
            "title", "2024년 하반기 개발자 컨퍼런스",
            "content", "최신 개발 트렌드와 기술을 공유하는 컨퍼런스입니다.",
            "organizer", "개발자 커뮤니티",
            "type", "STUDY",
            "url", "https://devconference2024.com",
            "startDate", "2024-12-01T09:00:00",
            "endDate", "2024-12-01T18:00:00"
        ));
        
        activities.add(Map.of(
            "title", "스타트업 아이디어 공모전",
            "content", "혁신적인 아이디어를 가진 창업팀을 모집합니다.",
            "organizer", "벤처협회",
            "type", "CONTEST",
            "url", "https://startup-contest.com",
            "startDate", "2024-11-25T00:00:00",
            "endDate", "2024-12-25T23:59:59"
        ));
        
        return activities;
    }
    
    // 수집 상태 조회
    public Map<String, Object> getIngestStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalActivities", activityRepository.count());
        status.put("campusActivities", activityRepository.findByIsCampus(true).size());
        status.put("externalActivities", activityRepository.findByIsCampus(false).size());
        status.put("lastUpdate", LocalDateTime.now());
        
        return status;
    }
}



