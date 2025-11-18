package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/ingest")
@Tag(name = "ingest", description = "데이터 수집")
@RequiredArgsConstructor
@Slf4j
public class IngestController {

    private final IngestService ingestService;

    @PostMapping("/trigger")
    @Operation(summary = "수집 파이프라인 트리거", description = "관리자용. 특정 소스 재수집 또는 전체 동기화를 트리거합니다.")
    public ResponseEntity<Map<String, Object>> triggerIngest(
            @RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            String source = null;
            Boolean fullResync = false;
            
            if (requestBody != null) {
                source = (String) requestBody.get("source");
                Object fullResyncObj = requestBody.get("fullResync");
                if (fullResyncObj instanceof Boolean) {
                    fullResync = (Boolean) fullResyncObj;
                } else if (fullResyncObj != null) {
                    fullResync = Boolean.parseBoolean(fullResyncObj.toString());
                }
            }
            
            Map<String, Object> config = Map.of(
                "source", source != null ? source : "",
                "fullResync", fullResync
            );
            
            Map<String, Object> result = ingestService.triggerIngest(source, config);
            return ResponseEntity.status(202).body(result);  // 202 Accepted (스펙에 맞춤)
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "데이터 수집 실패",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "수집 상태 조회", description = "현재 데이터 수집 상태를 조회합니다.")
    public ResponseEntity<Map<String, Object>> getIngestStatus() {
        try {
            Map<String, Object> status = ingestService.getIngestStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "상태 조회 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/campus")
    @Operation(summary = "교내 활동 수집", description = "교내 활동 데이터를 수집합니다.")
    public ResponseEntity<Map<String, Object>> ingestCampusActivities() {
        try {
            Map<String, Object> result = ingestService.triggerIngest("campus", Map.of());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "교내 활동 수집 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/external")
    @Operation(summary = "외부 활동 수집", description = "외부 사이트에서 활동 데이터를 크롤링합니다.")
    public ResponseEntity<Map<String, Object>> ingestExternalActivities(
            @Parameter(description = "크롤러 소스 (linkareer 등)") @RequestParam(defaultValue = "linkareer") String source,
            @Parameter(description = "수집 모드 (total: 전체, partial: 최신 일부)") @RequestParam(defaultValue = "partial") String mode) {
        try {
            Map<String, Object> config = Map.of("source", source, "mode", mode);
            Map<String, Object> result = ingestService.ingestExternalActivities(config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "외부 활동 수집 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping(value = "/manual", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "수동 활동 입력", description = "JSON 또는 엑셀 파일로 활동 데이터를 입력합니다.")
    public ResponseEntity<Map<String, Object>> ingestManualActivities(
            @Parameter(description = "엑셀 파일 (.xlsx, .xls)") 
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) Map<String, Object> activitiesData) {
        try {
            Map<String, Object> config;
            
            // 엑셀 파일이 있으면 엑셀 파싱
            if (file != null && !file.isEmpty()) {
                String filename = file.getOriginalFilename();
                if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "엑셀 파일만 업로드 가능합니다 (.xlsx, .xls)"
                    ));
                }
                
                // 엑셀 파일에서 데이터 추출
                java.util.List<Map<String, Object>> excelActivities = ingestService.parseExcelFile(file);
                config = Map.of("activities", excelActivities, "source", "excel");
            } 
            // JSON 데이터가 있으면 기존 로직 사용
            else if (activitiesData != null && activitiesData.containsKey("activities")) {
                config = Map.of("activities", activitiesData.get("activities"), "source", "json");
            } 
            else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "엑셀 파일 또는 activities 데이터가 필요합니다"
                ));
            }
            
            Map<String, Object> result = ingestService.triggerIngest("manual", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("수동 활동 입력 실패", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "수동 활동 입력 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/linkareer")
    @Operation(summary = "Linkareer 공모전 수집 (Deprecated)", description = "Linkareer에서 공모전 데이터를 크롤링합니다. /external?source=linkareer 사용을 권장합니다.")
    public ResponseEntity<Map<String, Object>> ingestLinkareerContests(
            @Parameter(description = "수집 모드 (total: 전체, partial: 최신 일부)") @RequestParam(defaultValue = "partial") String mode) {
        try {
            Map<String, Object> config = Map.of("source", "linkareer", "mode", mode);
            Map<String, Object> result = ingestService.ingestExternalActivities(config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Linkareer 공모전 수집 실패",
                "message", e.getMessage()
            ));
        }
    }
}