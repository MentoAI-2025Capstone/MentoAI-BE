package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<ActivityEntity, Long> {
    
    @Query("""
        SELECT DISTINCT a
        FROM ActivityEntity a
        LEFT JOIN a.activityTags at
        LEFT JOIN at.tag t
        LEFT JOIN a.dates d ON d.activity = a
        WHERE
            (:q IS NULL OR :q = '' OR
             LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(a.summary) LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(a.content) LIKE LOWER(CONCAT('%', :q, '%'))) AND
            (:type IS NULL OR a.type = :type) AND
            (:isCampus IS NULL OR a.isCampus = :isCampus) AND
            (:status IS NULL OR a.status = :status) AND
            (:tagNames IS NULL OR t.name IN :tagNames) AND
            (d.id IS NULL OR :deadlineType IS NULL OR d.dateType = :deadlineType) AND
            (:deadlineBefore IS NULL OR d.id IS NULL OR d.dateValue <= :deadlineBefore)
        """)
    Page<ActivityEntity> search(
        @Param("q") String query,
        @Param("type") ActivityType type,
            @Param("tagNames") List<String> tagNames,
        @Param("isCampus") Boolean isCampus,
        @Param("status") ActivityStatus status,
            @Param("deadlineBefore") LocalDateTime deadlineBefore,
            @Param("deadlineType") ActivityDateEntity.DateType deadlineType,
        Pageable pageable
    );
    
    List<ActivityEntity> findByStatus(ActivityStatus status);
    
    List<ActivityEntity> findByIsCampus(Boolean isCampus);
    
    boolean existsByUrl(String url);
    
    boolean existsByTitle(String title);

    default Page<ActivityEntity> findByFilters(
            String query,
            ActivityType type,
            Boolean isCampus,
            ActivityStatus status,
            Pageable pageable
    ) {
        return search(query, type, null, isCampus, status, null, null, pageable);
    }

    default Page<ActivityEntity> findByComplexFilters(
            String query,
            ActivityType type,
            Boolean isCampus,
            ActivityStatus status,
            List<String> tagNames,
            Pageable pageable
    ) {
        return search(query, type, tagNames, isCampus, status, null, null, pageable);
    }
}
