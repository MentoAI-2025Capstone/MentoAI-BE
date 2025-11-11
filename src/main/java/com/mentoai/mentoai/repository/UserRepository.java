package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    
    Optional<UserEntity> findByEmail(String email);
    
    boolean existsByEmail(String email);

    Optional<UserEntity> findByAuthProviderAndProviderUserId(UserEntity.AuthProvider authProvider, String providerUserId);

    @Query("SELECT u FROM UserEntity u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    Optional<UserEntity> findBySearchQuery(@Param("q") String query);
}





