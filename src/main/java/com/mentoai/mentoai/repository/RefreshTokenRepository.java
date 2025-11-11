package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.RefreshTokenEntity;
import com.mentoai.mentoai.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
    void deleteByUser(UserEntity user);
    void deleteByExpiresAtBefore(OffsetDateTime expiry);
}
