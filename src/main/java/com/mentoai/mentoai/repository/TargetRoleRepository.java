package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.TargetRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TargetRoleRepository extends JpaRepository<TargetRoleEntity, String> {
    
    Optional<TargetRoleEntity> findByNameIgnoreCase(String name);
    
    @Query("SELECT t FROM TargetRoleEntity t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.roleId) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<TargetRoleEntity> findByKeyword(@Param("keyword") String keyword);
}




