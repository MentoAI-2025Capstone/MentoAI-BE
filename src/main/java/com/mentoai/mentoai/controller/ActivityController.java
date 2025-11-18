package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.*;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/activities")
@Tag(name = "activities", description = "공모전/채용/교내행사 등 활동 리소스")
@RequiredArgsConstructor
@Validated
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "활동 목록 조회", description = "필터/정렬을 포함한 활동 목록을 반환합니다. userId가 제공되면 사용자 맞춤 추천이 적용됩니다.")
    public ResponseEntity<PagedActivitiesResponse> listActivities(
            @Parameter(description = "사용자 ID (선택, 제공 시 맞춤 추천 적용)") @RequestParam(required = false) Long userId,
            @Parameter(description = "제목/내용 전체 검색어") @RequestParam(required = false) String q,
            @Parameter(description = "활동 유형 필터") @RequestParam(required = false) ActivityType type,
            @Parameter(description = "태그 이름(복수 지정 시 콤마 구분)") @RequestParam(required = false) String tag,
            @Parameter(description = "교내 활동 여부") @RequestParam(required = false) Boolean isCampus,
            @Parameter(description = "활동 상태") @RequestParam(required = false) ActivityStatus status,
            @Parameter(description = "마감일 상한 (YYYY-MM-DD)") @RequestParam(required = false) String deadlineBefore,
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "정렬 키 (createdAt, deadline, popularity 등)") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        List<String> tagNames = null;
        if (tag != null && !tag.trim().isEmpty()) {
            tagNames = Arrays.asList(tag.split(","));
        }
        
        String sortField = "createdAt";
        String direction = "desc";
        if (sort != null && !sort.isBlank()) {
            String[] sortParts = sort.split(",", 2);
            sortField = sortParts[0];
            if (sortParts.length > 1) {
                direction = sortParts[1];
            }
        }

        LocalDate deadline = null;
        if (deadlineBefore != null && !deadlineBefore.isBlank()) {
            deadline = LocalDate.parse(deadlineBefore);
        }

        Page<com.mentoai.mentoai.entity.ActivityEntity> result = activityService.getActivities(
                userId,
                q,
                type,
                tagNames,
                isCampus,
                status,
                deadline,
                Math.max(page - 1, 0),
                size,
                sortField,
                direction
        );

        List<ActivityResponse> items = result.stream()
                .map(ActivityMapper::toResponse)
                .toList();

        PagedActivitiesResponse response = new PagedActivitiesResponse(
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                items
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "활동 생성", description = "새로운 활동을 생성합니다.")
    public ResponseEntity<ActivityResponse> createActivity(@Valid @RequestBody ActivityUpsertRequest request) {
        try {
            var created = activityService.createActivity(request);
            return ResponseEntity.status(201).body(ActivityMapper.toResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "활동 상세 조회", description = "특정 활동의 상세 정보를 반환합니다.")
    public ResponseEntity<ActivityResponse> getActivity(
            @Parameter(description = "활동 ID") @PathVariable("activityId") Long activityId) {
        Optional<com.mentoai.mentoai.entity.ActivityEntity> activity = activityService.getActivity(activityId);
        return activity.map(ActivityMapper::toResponse)
                       .map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{activityId}")
    @Operation(summary = "활동 수정", description = "기존 활동 정보를 수정합니다.")
    public ResponseEntity<ActivityResponse> updateActivity(
            @Parameter(description = "활동 ID") @PathVariable("activityId") Long activityId,
            @Valid @RequestBody ActivityUpsertRequest request) {
        try {
            Optional<com.mentoai.mentoai.entity.ActivityEntity> updatedActivity = activityService.updateActivity(activityId, request);
            return updatedActivity.map(ActivityMapper::toResponse)
                                 .map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{activityId}")
    @Operation(summary = "활동 삭제", description = "활동을 삭제합니다.")
    public ResponseEntity<Void> deleteActivity(
            @Parameter(description = "활동 ID") @PathVariable("activityId") Long activityId) {
        boolean deleted = activityService.deleteActivity(activityId);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{activityId}/attachments")
    @Operation(summary = "첨부 목록 조회", description = "특정 활동의 첨부파일 목록을 반환합니다.")
    public ResponseEntity<List<AttachmentResponse>> listAttachments(
            @Parameter(description = "활동 ID") @PathVariable("activityId") Long activityId) {
        try {
            List<AttachmentResponse> attachments = activityService.getAttachments(activityId).stream()
                    .map(ActivityMapper::toAttachmentResponse)
                    .toList();
            return ResponseEntity.ok(attachments);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{activityId}/attachments")
    @Operation(summary = "첨부 추가", description = "활동에 첨부파일을 추가합니다.")
    public ResponseEntity<AttachmentResponse> addAttachment(
            @Parameter(description = "활동 ID") @PathVariable("activityId") Long activityId,
            @Valid @RequestBody AttachmentUpsertRequest request) {
        try {
            var attachment = activityService.addAttachment(activityId, request);
            return ResponseEntity.status(201).body(ActivityMapper.toAttachmentResponse(attachment));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
