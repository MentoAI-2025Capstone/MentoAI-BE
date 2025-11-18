package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {
    
    List<ChatSessionEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);
    
    @Query("SELECT s FROM ChatSessionEntity s WHERE s.userId = :userId ORDER BY s.updatedAt DESC")
    List<ChatSessionEntity> findUserSessions(@Param("userId") Long userId);
}


