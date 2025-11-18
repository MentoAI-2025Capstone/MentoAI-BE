package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.ActivityTagId;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import com.mentoai.mentoai.service.LinkareerCrawlerService.LinkareerActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final LinkareerCrawlerService linkareerCrawlerService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // 데이터 수집 트리거
    @Transactional
    public Map<String, Object> triggerIngest(String source, Map<String, Object> config) {
        log.info("Ingestion started: source={}, config={}", source, config);
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "started");
        result.put("timestamp", LocalDateTime.now());
        result.put("source", source);
        
        // 비동기로 데이터 수집 실행
        CompletableFuture.runAsync(() -> {
            try {
                performDataIngest(source, config);
            } catch (Exception e) {
                log.error("Error occurred during data ingestion", e);
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
                case "linkareer":
                    ingestLinkareerContests(config);
                    break;
                default:
                    log.warn("Unknown ingestion source: {}", source);
            }
        } catch (Exception e) {
            log.error("Ingestion failed: source={}", source, e);
        }
    }
    
    // 교내 활동 수집
    private void ingestCampusActivities(Map<String, Object> config) {
        log.info("Campus activities ingestion started");
        
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
                
                log.debug("Created campus activity: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("Failed to create campus activity: {}", activityData, e);
            }
        }
        
        log.info("Campus activities ingestion finished: {} created", created);
    }
    
    // 외부 활동 수집
    private void ingestExternalActivities(Map<String, Object> config) {
        log.info("External activities ingestion started");
        
        // 실제로는 외부 사이트들을 크롤링
        List<Map<String, Object>> externalActivities = generateSampleExternalActivities();
        
        int created = 0;
        for (Map<String, Object> activityData : externalActivities) {
            try {
                ActivityEntity activity = createActivityFromData(activityData);
                activity.setIsCampus(false);
                
                activityRepository.save(activity);
                created++;
                
                log.debug("Created external activity: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("Failed to create external activity: {}", activityData, e);
            }
        }
        
        log.info("External activities ingestion finished: {} created", created);
    }
    
    // 수동 입력 활동 수집
    private void ingestManualActivities(Map<String, Object> config) {
        log.info("Manual activities ingestion started");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> manualActivities = (List<Map<String, Object>>) config.get("activities");
        
        if (manualActivities == null || manualActivities.isEmpty()) {
            log.warn("No manual activity data found");
            return;
        }
        
        int created = 0;
        for (Map<String, Object> activityData : manualActivities) {
            try {
                ActivityEntity activity = createActivityFromData(activityData);
                activityRepository.save(activity);
                created++;
                
                log.debug("Created manual activity: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("Failed to create manual activity: {}", activityData, e);
            }
        }
        
        log.info("Manual activities ingestion finished: {} created", created);
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
        activity.setStatus(ActivityEntity.ActivityStatus.OPEN);
        
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
    
    // Linkareer 공모전 수집
    @Transactional
    public Map<String, Object> ingestLinkareerContests(Map<String, Object> config) {
        log.info("Linkareer contests ingestion started");
        
        String mode = config != null ? (String) config.get("mode") : "partial";
        List<LinkareerActivity> linkareerActivities;
        
        if ("total".equalsIgnoreCase(mode)) {
            linkareerActivities = linkareerCrawlerService.crawlAllContests();
        } else {
            linkareerActivities = linkareerCrawlerService.crawlRecentContests();
        }
        
        int created = 0;
        int skipped = 0;
        
        for (LinkareerActivity linkareerActivity : linkareerActivities) {
            try {
                // 중복 체크 (URL 기반)
                String url = linkareerActivity.id() != null 
                        ? "https://linkareer.com/activity/" + linkareerActivity.id()
                        : null;
                
                if (url != null && activityRepository.existsByUrl(url)) {
                    skipped++;
                    log.debug("Skipped duplicate activity: {}", linkareerActivity.title());
                    continue;
                }
                
                ActivityEntity activity = convertLinkareerToActivity(linkareerActivity);
                activityRepository.save(activity);
                created++;
                
                log.debug("Created Linkareer activity: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("Failed to create Linkareer activity: {}", linkareerActivity.title(), e);
            }
        }
        
        log.info("Linkareer contests ingestion finished: {} created, {} skipped", created, skipped);
        
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("total", linkareerActivities.size());
        return result;
    }
    
    // LinkareerActivity를 ActivityEntity로 변환
    private ActivityEntity convertLinkareerToActivity(LinkareerActivity linkareer) {
        ActivityEntity activity = new ActivityEntity();
        
        activity.setTitle(linkareer.title());
        activity.setOrganizer(linkareer.organizationName());
        activity.setType(ActivityEntity.ActivityType.CONTEST);
        activity.setStatus(ActivityEntity.ActivityStatus.OPEN);
        activity.setIsCampus(false);
        
        // URL 생성
        if (linkareer.id() != null) {
            activity.setUrl("https://linkareer.com/activity/" + linkareer.id());
        }
        
        // 마감일 설정
        if (linkareer.recruitCloseAt() != null) {
            LocalDateTime closeDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(linkareer.recruitCloseAt()),
                    ZoneId.systemDefault()
            );
            
            ActivityDateEntity dateEntity = new ActivityDateEntity();
            dateEntity.setActivity(activity);
            dateEntity.setDateType(ActivityDateEntity.DateType.APPLY_END);
            dateEntity.setDateValue(closeDate);
            
            if (activity.getDates() == null) {
                activity.setDates(new ArrayList<>());
            }
            activity.getDates().add(dateEntity);
        }
        
        // 태그 생성 또는 찾기
        if (StringUtils.hasText(linkareer.field())) {
            TagEntity tag = findOrCreateTag(linkareer.field(), TagEntity.TagType.CATEGORY);
            if (tag != null) {
                ActivityTagEntity activityTag = new ActivityTagEntity();
                activityTag.setActivity(activity);
                activityTag.setTag(tag);
                activityTag.setId(new ActivityTagId());
                
                if (activity.getActivityTags() == null) {
                    activity.setActivityTags(new ArrayList<>());
                }
                activity.getActivityTags().add(activityTag);
            }
        }
        
        return activity;
    }
    
    // 태그 찾기 또는 생성
    private TagEntity findOrCreateTag(String tagName, TagEntity.TagType tagType) {
        if (!StringUtils.hasText(tagName)) {
            return null;
        }
        
        String normalizedName = tagName.trim();
        
        // 기존 태그 찾기
        Optional<TagEntity> existingTag = tagRepository.findByName(normalizedName);
        if (existingTag.isPresent()) {
            return existingTag.get();
        }
        
        // 새 태그 생성
        TagEntity newTag = new TagEntity();
        newTag.setName(normalizedName);
        newTag.setType(tagType);
        
        try {
            return tagRepository.save(newTag);
        } catch (Exception e) {
            log.warn("Failed to create tag: {}", normalizedName, e);
            return null;
        }
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



