package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.ActivityTagId;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import com.mentoai.mentoai.service.crawler.ExternalActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
    private final ExternalCrawlerService externalCrawlerService;
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
                case "linkareer":
                    // 외부 크롤러를 사용하는 경우 public 메서드 호출
                    if (config != null && config.containsKey("source")) {
                        // 외부 크롤러 사용
                        ingestExternalActivities(config);
                    } else {
                        // 샘플 데이터 사용 (deprecated)
                        ingestExternalActivitiesSample(config);
                    }
                    break;
                case "manual":
                    ingestManualActivities(config);
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
    
    // 외부 활동 수집 (샘플 데이터용 - deprecated)
    private void ingestExternalActivitiesSample(Map<String, Object> config) {
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
        int skipped = 0;
        
        for (Map<String, Object> activityData : manualActivities) {
            try {
                // 중복 체크 (URL 또는 제목 기반)
                String url = (String) activityData.get("url");
                String title = (String) activityData.get("title");
                
                if (url != null && !url.trim().isEmpty() && activityRepository.existsByUrl(url)) {
                    skipped++;
                    log.debug("Skipped duplicate activity (URL): {}", title);
                    continue;
                }
                
                if (title != null && !title.trim().isEmpty() && activityRepository.existsByTitle(title)) {
                    skipped++;
                    log.debug("Skipped duplicate activity (Title): {}", title);
                    continue;
                }
                
                ActivityEntity activity = createActivityFromData(activityData);
                activityRepository.save(activity);
                created++;
                
                log.debug("Created manual activity: {}", activity.getTitle());
            } catch (Exception e) {
                log.error("Failed to create manual activity: {}", activityData, e);
            }
        }
        
        log.info("Manual activities ingestion finished: {} created, {} skipped", created, skipped);
    }
    
    /**
     * 엑셀 파일을 파싱하여 활동 데이터 리스트로 변환
     * 엑셀 형식 예시:
     * - 첫 번째 행: 헤더 (title, content, organizer, type, url, startDate, endDate, field, ...)
     * - 두 번째 행부터: 데이터
     */
    public List<Map<String, Object>> parseExcelFile(MultipartFile file) throws IOException {
        List<Map<String, Object>> activities = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook;
            
            // 파일 확장자에 따라 적절한 Workbook 생성
            String filename = file.getOriginalFilename();
            if (filename != null && filename.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                workbook = new HSSFWorkbook(inputStream);
            }
            
            Sheet sheet = workbook.getSheetAt(0); // 첫 번째 시트 사용
            
            if (sheet.getPhysicalNumberOfRows() < 2) {
                workbook.close();
                throw new IllegalArgumentException("엑셀 파일에 데이터가 없습니다 (최소 2행 필요: 헤더 + 데이터)");
            }
            
            // 헤더 행 읽기
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }
            
            // 데이터 행 읽기
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Map<String, Object> activityData = new HashMap<>();
                
                for (int j = 0; j < headers.size() && j < row.getPhysicalNumberOfCells(); j++) {
                    String header = headers.get(j);
                    Cell cell = row.getCell(j);
                    
                    if (header != null && !header.trim().isEmpty() && cell != null) {
                        String value = getCellValueAsString(cell);
                        if (value != null && !value.trim().isEmpty()) {
                            activityData.put(header.trim().toLowerCase(), value);
                        }
                    }
                }
                
                // 최소한 title은 있어야 함
                if (activityData.containsKey("title") && activityData.get("title") != null) {
                    activities.add(activityData);
                }
            }
            
            workbook.close();
        }
        
        return activities;
    }
    
    /**
     * Cell 값을 String으로 변환
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 날짜 형식인 경우
                    return cell.getDateCellValue().toString();
                } else {
                    // 숫자인 경우
                    double numericValue = cell.getNumericCellValue();
                    // 정수인지 확인
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    // 데이터로부터 활동 엔티티 생성
    private ActivityEntity createActivityFromData(Map<String, Object> data) {
        ActivityEntity activity = new ActivityEntity();
        
        activity.setTitle((String) data.get("title"));
        activity.setContent((String) data.get("content"));
        
        // organizer 매핑 (organization 또는 organizer 모두 지원)
        String organizer = (String) data.get("organizer");
        if (organizer == null || organizer.trim().isEmpty()) {
            organizer = (String) data.get("organization");
        }
        activity.setOrganizer(organizer);
        
        activity.setUrl((String) data.get("url"));
        
        // 타입 설정 (category 또는 type 모두 지원)
        String typeStr = (String) data.get("type");
        if (typeStr == null || typeStr.trim().isEmpty()) {
            typeStr = (String) data.get("category");
        }
        
        if (typeStr != null) {
            // 한글 카테고리명을 영어 타입으로 변환
            typeStr = convertCategoryToType(typeStr);
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
        
        // deadline 처리
        String deadlineStr = (String) data.get("deadline");
        if (deadlineStr != null && !deadlineStr.trim().isEmpty()) {
            try {
                // YYYY-MM-DD 형식 파싱
                java.time.LocalDate deadlineDate = java.time.LocalDate.parse(deadlineStr);
                LocalDateTime deadlineDateTime = deadlineDate.atTime(23, 59, 59);
                
                ActivityDateEntity dateEntity = new ActivityDateEntity();
                dateEntity.setActivity(activity);
                dateEntity.setDateType(ActivityDateEntity.DateType.APPLY_END);
                dateEntity.setDateValue(deadlineDateTime);
                
                if (activity.getDates() == null) {
                    activity.setDates(new ArrayList<>());
                }
                activity.getDates().add(dateEntity);
            } catch (Exception e) {
                log.warn("Failed to parse deadline: {}", deadlineStr, e);
            }
        }
        
        return activity;
    }
    
    /**
     * 한글 카테고리명을 영어 타입으로 변환
     */
    private String convertCategoryToType(String category) {
        if (category == null) {
            return "STUDY";
        }
        
        String lowerCategory = category.toLowerCase().trim();
        
        // 카테고리 매핑
        if (lowerCategory.contains("공모전") || lowerCategory.contains("contest")) {
            return "CONTEST";
        } else if (lowerCategory.contains("채용") || lowerCategory.contains("job") || lowerCategory.contains("인턴")) {
            return "JOB";
        } else if (lowerCategory.contains("스터디") || lowerCategory.contains("study")) {
            return "STUDY";
        } else if (lowerCategory.contains("교내") || lowerCategory.contains("campus")) {
            return "CAMPUS";
        }
        
        // 기본값
        return "STUDY";
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
    
    // 외부 사이트 활동 수집 (Linkareer 등)
    @Transactional
    public Map<String, Object> ingestExternalActivities(Map<String, Object> config) {
        String source = config != null ? (String) config.get("source") : "linkareer";
        String mode = config != null ? (String) config.get("mode") : "partial";
        
        log.info("External activities ingestion started: source={}, mode={}", source, mode);
        
        List<ExternalActivity> externalActivities;
        
        if ("total".equalsIgnoreCase(mode)) {
            externalActivities = externalCrawlerService.crawlAll(source);
        } else {
            externalActivities = externalCrawlerService.crawlRecent(source);
        }
        
        int created = 0;
        int skipped = 0;
        
        for (ExternalActivity externalActivity : externalActivities) {
            try {
                // 중복 체크 (URL 기반)
                String url = externalActivity.getUrlOrDefault();
                
                if (url != null && activityRepository.existsByUrl(url)) {
                    skipped++;
                    log.debug("Skipped duplicate activity: {}", externalActivity.title());
                    continue;
                }
                
                ActivityEntity activity = convertExternalActivityToEntity(externalActivity);
                activityRepository.save(activity);
                created++;
                
                log.debug("Created external activity from {}: {}", source, activity.getTitle());
            } catch (Exception e) {
                log.error("Failed to create external activity from {}: {}", source, externalActivity.title(), e);
            }
        }
        
        log.info("External activities ingestion finished: source={}, {} created, {} skipped", source, created, skipped);
        
        Map<String, Object> result = new HashMap<>();
        result.put("source", source);
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("total", externalActivities.size());
        return result;
    }
    
    // ExternalActivity를 ActivityEntity로 변환
    private ActivityEntity convertExternalActivityToEntity(ExternalActivity external) {
        ActivityEntity activity = new ActivityEntity();
        
        activity.setTitle(external.title());
        activity.setOrganizer(external.organizationName());
        activity.setType(ActivityEntity.ActivityType.CONTEST);
        activity.setStatus(ActivityEntity.ActivityStatus.OPEN);
        activity.setIsCampus(false);
        
        // URL 설정
        String url = external.getUrlOrDefault();
        if (url != null) {
            activity.setUrl(url);
        }
        
        // 마감일 설정
        if (external.recruitCloseAt() != null) {
            LocalDateTime closeDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(external.recruitCloseAt()),
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
        if (StringUtils.hasText(external.field())) {
            TagEntity tag = findOrCreateTag(external.field(), TagEntity.TagType.CATEGORY);
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



