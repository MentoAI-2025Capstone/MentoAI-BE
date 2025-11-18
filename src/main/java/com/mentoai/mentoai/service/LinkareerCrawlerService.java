package com.mentoai.mentoai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkareerCrawlerService {

    private final ObjectMapper objectMapper;

    @Value("${linkareer.crawler.path:../Mentoai-DE}")
    private String crawlerPath;

    @Value("${linkareer.crawler.python.path:python3}")
    private String pythonPath;

    /**
     * Linkareer 전체 공모전 크롤링
     * @return 크롤링된 활동 목록
     */
    public List<LinkareerActivity> crawlAllContests() {
        return executeCrawlerScript("total_linkareer.py");
    }

    /**
     * Linkareer 최신 공모전 일부 크롤링 (30개)
     * @return 크롤링된 활동 목록
     */
    public List<LinkareerActivity> crawlRecentContests() {
        return executeCrawlerScript("partial_linkareer.py");
    }

    /**
     * Python 크롤러 스크립트 실행
     * @param scriptName 실행할 스크립트 파일명
     * @return 크롤링된 활동 목록
     */
    private List<LinkareerActivity> executeCrawlerScript(String scriptName) {
        List<LinkareerActivity> activities = new ArrayList<>();
        
        try {
            Path scriptPath = Paths.get(crawlerPath, scriptName);
            log.info("Executing crawler script: {}", scriptPath);
            
            // Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonPath,
                    scriptPath.toString()
            );
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("Crawler output: {}", line);
                }
            }
            
            // 프로세스 종료 대기
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.error("Crawler script failed with exit code: {}", exitCode);
                log.error("Output: {}", output.toString());
                return activities;
            }
            
            // JSON 파싱
            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                log.warn("Crawler script returned empty output");
                return activities;
            }
            
            // JSON 배열 파싱
            List<Map<String, Object>> rawActivities = objectMapper.readValue(
                    jsonOutput,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            
            // LinkareerActivity로 변환
            for (Map<String, Object> raw : rawActivities) {
                try {
                    LinkareerActivity activity = parseLinkareerActivity(raw);
                    if (activity != null) {
                        activities.add(activity);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse activity: {}", raw, e);
                }
            }
            
            log.info("Successfully crawled {} activities from {}", activities.size(), scriptName);
            
        } catch (Exception e) {
            log.error("Error executing crawler script: {}", scriptName, e);
        }
        
        return activities;
    }

    /**
     * 원시 데이터를 LinkareerActivity로 파싱
     */
    private LinkareerActivity parseLinkareerActivity(Map<String, Object> raw) {
        try {
            String title = getStringValue(raw, "title");
            String id = getStringValue(raw, "id");
            Long recruitCloseAt = getLongValue(raw, "recruitCloseAt");
            String organizationName = getStringValue(raw, "organizationName");
            String field = getStringValue(raw, "field");
            
            if (title == null || title.trim().isEmpty()) {
                return null;
            }
            
            return new LinkareerActivity(
                    title,
                    id,
                    recruitCloseAt,
                    organizationName,
                    field
            );
        } catch (Exception e) {
            log.warn("Failed to parse LinkareerActivity from raw data: {}", raw, e);
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Linkareer 활동 데이터 DTO
     */
    public record LinkareerActivity(
            String title,
            String id,  // Linkareer 활동 ID
            Long recruitCloseAt,  // 밀리초 타임스탬프
            String organizationName,
            String field  // 분야
    ) {}
}

