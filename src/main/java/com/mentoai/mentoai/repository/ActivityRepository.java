package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<ActivityEntity, Long> {
    
    // 기본 검색 (제목, 내용)
    @Query("SELECT a FROM ActivityEntity a WHERE " +
           "(:q IS NULL OR :q = '' OR " +
           "LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:type IS NULL OR a.type = :type) AND " +
           "(:isCampus IS NULL OR a.isCampus = :isCampus) AND " +
           "(:status IS NULL OR a.status = :status)")
    Page<ActivityEntity> findByFilters(
        @Param("q") String query,
        @Param("type") ActivityType type,
        @Param("isCampus") Boolean isCampus,
        @Param("status") ActivityStatus status,
        Pageable pageable
    );
    
    // 태그로 검색
    @Query("SELECT DISTINCT a FROM ActivityEntity a " +
           "JOIN a.activityTags at " +
           "JOIN at.tag t " +
           "WHERE t.name IN :tagNames")
    Page<ActivityEntity> findByTagNames(@Param("tagNames") List<String> tagNames, Pageable pageable);
    
    // 복합 검색 (검색어 + 태그)
    @Query("SELECT DISTINCT a FROM ActivityEntity a " +
           "LEFT JOIN a.activityTags at " +
           "LEFT JOIN at.tag t " +
           "WHERE " +
           "(:q IS NULL OR :q = '' OR " +
           "LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:type IS NULL OR a.type = :type) AND " +
           "(:isCampus IS NULL OR a.isCampus = :isCampus) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:tagNames IS NULL OR t.name IN :tagNames)")
    Page<ActivityEntity> findByComplexFilters(
        @Param("q") String query,
        @Param("type") ActivityType type,
        @Param("isCampus") Boolean isCampus,
        @Param("status") ActivityStatus status,
        @Param("tagNames") List<String> tagNames,
        Pageable pageable
    );
    
    // 활성 상태인 활동들만 조회
    List<ActivityEntity> findByStatus(ActivityStatus status);
    
    // 교내/교외 활동 조회
    List<ActivityEntity> findByIsCampus(Boolean isCampus);
}



