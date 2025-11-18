package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    
    List<ChatMessageEntity> findBySession_SessionIdOrderByCreatedAtAsc(Long sessionId);
    
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.sessionId = :sessionId ORDER BY m.createdAt ASC")
    List<ChatMessageEntity> findSessionMessages(@Param("sessionId") Long sessionId);
}


