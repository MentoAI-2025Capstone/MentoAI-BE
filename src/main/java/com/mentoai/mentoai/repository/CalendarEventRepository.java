package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.CalendarEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEventEntity, Long> {
    
    // 특정 사용자의 모든 캘린더 이벤트 조회
    List<CalendarEventEntity> findByUserId(Long userId);
    
    // 특정 사용자의 특정 날짜 범위 이벤트 조회
    @Query("SELECT ce FROM CalendarEventEntity ce WHERE ce.userId = :userId " +
           "AND (:startDate IS NULL OR ce.startTime >= :startDate) " +
           "AND (:endDate IS NULL OR ce.endTime <= :endDate) " +
           "ORDER BY ce.startTime ASC")
    List<CalendarEventEntity> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    // 특정 사용자의 특정 날짜 이벤트 조회
    @Query("SELECT ce FROM CalendarEventEntity ce WHERE ce.userId = :userId " +
           "AND DATE(ce.startTime) = DATE(:date) " +
           "ORDER BY ce.startTime ASC")
    List<CalendarEventEntity> findByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDateTime date);
    
    // 특정 사용자의 특정 월 이벤트 조회
    @Query("SELECT ce FROM CalendarEventEntity ce WHERE ce.userId = :userId " +
           "AND YEAR(ce.startTime) = :year AND MONTH(ce.startTime) = :month " +
           "ORDER BY ce.startTime ASC")
    List<CalendarEventEntity> findByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);
    
    // 특정 사용자의 다가오는 이벤트 조회
    @Query("SELECT ce FROM CalendarEventEntity ce WHERE ce.userId = :userId " +
           "AND ce.startTime > :now " +
           "ORDER BY ce.startTime ASC")
    List<CalendarEventEntity> findUpcomingEventsByUserId(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);
    
    // 특정 사용자의 특정 활동과 연결된 이벤트 조회
    List<CalendarEventEntity> findByUserIdAndActivityId(Long userId, Long activityId);
    
    // 특정 사용자의 이벤트 개수 조회
    long countByUserId(Long userId);
}





