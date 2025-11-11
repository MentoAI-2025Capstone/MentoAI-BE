package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.entity.NotificationEntity;
import com.mentoai.mentoai.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Tag(name = "notifications", description = "사용자 알림")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 알림 목록", description = "사용자의 알림 목록을 조회합니다.")
    public ResponseEntity<?> getUserNotifications(
            @Parameter(description = "사용자 ID") @PathVariable Long userId,
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        try {
            var notifications = notificationService.getUserNotifications(userId, page, size);
            return ResponseEntity.ok(notifications);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "알림 조회 실패",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{userId}/unread-count")
    @Operation(summary = "읽지 않은 알림 개수", description = "사용자의 읽지 않은 알림 개수를 조회합니다.")
    public ResponseEntity<?> getUnreadCount(
            @Parameter(description = "사용자 ID") @PathVariable Long userId) {
        try {
            Long count = notificationService.getUnreadCount(userId);
            return ResponseEntity.ok(Map.of("unreadCount", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "알림 개수 조회 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
    public ResponseEntity<?> markAsRead(
            @Parameter(description = "알림 ID") @PathVariable Long notificationId,
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        try {
            boolean success = notificationService.markAsRead(notificationId, userId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "알림이 읽음 처리되었습니다."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "알림을 찾을 수 없거나 권한이 없습니다."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "알림 읽음 처리 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/{userId}/read-all")
    @Operation(summary = "모든 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 처리합니다.")
    public ResponseEntity<?> markAllAsRead(
            @Parameter(description = "사용자 ID") @PathVariable Long userId) {
        try {
            notificationService.markAllAsRead(userId);
            return ResponseEntity.ok(Map.of("message", "모든 알림이 읽음 처리되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "알림 읽음 처리 실패",
                "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "알림 삭제", description = "특정 알림을 삭제합니다.")
    public ResponseEntity<?> deleteNotification(
            @Parameter(description = "알림 ID") @PathVariable Long notificationId,
            @Parameter(description = "사용자 ID") @RequestParam Long userId) {
        try {
            boolean success = notificationService.deleteNotification(notificationId, userId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "알림이 삭제되었습니다."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "알림을 찾을 수 없거나 권한이 없습니다."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "알림 삭제 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/system-announcement")
    @Operation(summary = "시스템 공지 생성", description = "모든 사용자에게 시스템 공지를 전송합니다.")
    public ResponseEntity<?> createSystemAnnouncement(@RequestBody Map<String, String> announcement) {
        try {
            String title = announcement.get("title");
            String message = announcement.get("message");
            
            if (title == null || message == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "제목과 내용은 필수입니다."));
            }
            
            notificationService.createSystemAnnouncement(title, message);
            return ResponseEntity.ok(Map.of("message", "시스템 공지가 전송되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "시스템 공지 전송 실패",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/cleanup")
    @Operation(summary = "오래된 알림 정리", description = "30일 이상 된 알림을 보관 처리합니다.")
    public ResponseEntity<?> cleanupOldNotifications() {
        try {
            notificationService.cleanupOldNotifications();
            return ResponseEntity.ok(Map.of("message", "오래된 알림 정리가 완료되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "알림 정리 실패",
                "message", e.getMessage()
            ));
        }
    }
}



