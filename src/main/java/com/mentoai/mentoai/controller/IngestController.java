package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ingest")
@Tag(name = "ingest", description = "데이터 수집")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;

    @PostMapping("/trigger")
    @Operation(summary = "데이터 수집 트리거", description = "지정된 소스에서 데이터를 수집합니다.")
    public ResponseEntity<Map<String, Object>> triggerIngest(
            @Parameter(description = "수집 소스 (campus, external, manual, linkareer)") @RequestParam String source,
            @RequestBody(required = false) Map<String, Object> config) {
        try {
            Map<String, Object> result = ingestService.triggerIngest(source, config != null ? config : Map.of());
            return ResponseEntity.ok(result);
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
    @Operation(summary = "외부 활동 수집", description = "외부 활동 데이터를 수집합니다.")
    public ResponseEntity<Map<String, Object>> ingestExternalActivities() {
        try {
            Map<String, Object> result = ingestService.triggerIngest("external", Map.of());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "외부 활동 수집 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/manual")
    @Operation(summary = "수동 활동 입력", description = "수동으로 활동 데이터를 입력합니다.")
    public ResponseEntity<Map<String, Object>> ingestManualActivities(
            @RequestBody Map<String, Object> activitiesData) {
        try {
            Map<String, Object> config = Map.of("activities", activitiesData.get("activities"));
            Map<String, Object> result = ingestService.triggerIngest("manual", config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "수동 활동 입력 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/linkareer")
    @Operation(summary = "Linkareer 공모전 수집", description = "Linkareer에서 공모전 데이터를 크롤링합니다.")
    public ResponseEntity<Map<String, Object>> ingestLinkareerContests(
            @Parameter(description = "수집 모드 (total: 전체, partial: 최신 일부)") @RequestParam(defaultValue = "partial") String mode) {
        try {
            Map<String, Object> config = Map.of("mode", mode);
            Map<String, Object> result = ingestService.ingestLinkareerContests(config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Linkareer 공모전 수집 실패",
                "message", e.getMessage()
            ));
        }
    }
}