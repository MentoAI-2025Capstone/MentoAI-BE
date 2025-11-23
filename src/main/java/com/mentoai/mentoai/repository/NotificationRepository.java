package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.NotificationEntity;
import com.mentoai.mentoai.entity.NotificationEntity.NotificationStatus;
import com.mentoai.mentoai.entity.NotificationEntity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    
    // 사용자별 알림 조회 (읽지 않은 것 우선)
    @Query("SELECT n FROM NotificationEntity n WHERE n.userId = :userId AND n.status = :status ORDER BY n.isRead ASC, n.createdAt DESC")
    Page<NotificationEntity> findByUserIdAndStatusOrderByReadAndCreatedAt(
            @Param("userId") Long userId, 
            @Param("status") NotificationStatus status, 
            Pageable pageable);
    
    // 사용자별 읽지 않은 알림 개수
    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.userId = :userId AND n.isRead = false AND n.status = :status")
    Long countUnreadByUserIdAndStatus(@Param("userId") Long userId, @Param("status") NotificationStatus status);
    
    // 특정 타입의 알림 조회
    List<NotificationEntity> findByUserIdAndTypeAndStatus(Long userId, NotificationType type, NotificationStatus status);
    
    // 마감 임박 알림을 위한 활동 조회
    @Query("SELECT n FROM NotificationEntity n WHERE n.type = 'DEADLINE_REMINDER' AND n.activityId = :activityId AND n.status = 'ACTIVE'")
    List<NotificationEntity> findDeadlineRemindersByActivityId(@Param("activityId") Long activityId);
    
    // 오래된 알림 조회 (30일 이상)
    @Query("SELECT n FROM NotificationEntity n WHERE n.createdAt < :cutoffDate AND n.status = 'ACTIVE'")
    List<NotificationEntity> findOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // 사용자별 알림 삭제
    void deleteByUserIdAndStatus(Long userId, NotificationStatus status);
}

























