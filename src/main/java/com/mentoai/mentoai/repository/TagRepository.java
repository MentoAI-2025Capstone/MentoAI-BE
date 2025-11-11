package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.entity.TagEntity.TagType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long> {
    
    Optional<TagEntity> findByName(String name);
    
    List<TagEntity> findByType(TagType type);
    
    @Query("SELECT t FROM TagEntity t WHERE " +
           "(:q IS NULL OR :q = '' OR " +
           "LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:type IS NULL OR t.type = :type)")
    Page<TagEntity> findByFilters(
        @Param("q") String query,
        @Param("type") TagType type,
        Pageable pageable
    );
    
    boolean existsByName(String name);
}



