package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.dto.UserProfileUpsertRequest;
import com.mentoai.mentoai.controller.mapper.UserProfileMapper;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserProfileEntity;
import com.mentoai.mentoai.repository.UserProfileRepository;
import com.mentoai.mentoai.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public UserProfileResponse getProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userProfileRepository.findById(userId)
                .map(UserProfileMapper::toResponse)
                .orElse(UserProfileMapper.empty(user));
    }

    @Transactional
    public UserProfileResponse upsertProfile(Long userId, UserProfileUpsertRequest request) {
        try {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // 기존 프로필 존재 여부 확인 (findById로 실제 엔티티 조회)
            UserProfileEntity profile = userProfileRepository.findById(userId).orElse(null);
            
            if (profile == null) {
                // 새로 생성된 경우: @MapsId를 활용하여 user만 설정
                profile = new UserProfileEntity();
                // ⚠️ setUserId() 호출하지 않음! @MapsId가 자동으로 설정함
                profile.setUser(user); // 이것만으로 userId가 자동으로 채워짐
                profile.setInterestDomains(new ArrayList<>());
                profile.setTechStack(new ArrayList<>());
                profile.setAwards(new ArrayList<>());
                profile.setCertifications(new ArrayList<>());
                profile.setExperiences(new ArrayList<>());
                
                // EntityManager.persist()를 직접 사용하여 INSERT 보장
                entityManager.persist(profile);
                entityManager.flush(); // 즉시 DB에 반영
                log.debug("Created new profile for user: {}", userId);
            } else {
                // 기존 프로필 로드됨
                // Lazy 로딩된 컬렉션들을 강제로 로드
                if (profile.getAwards() != null) {
                    profile.getAwards().size(); // Lazy 로딩 강제
                }
                if (profile.getCertifications() != null) {
                    profile.getCertifications().size(); // Lazy 로딩 강제
                }
                if (profile.getExperiences() != null) {
                    profile.getExperiences().size(); // Lazy 로딩 강제
                }
                
                // 기존 자식 엔티티들을 명시적으로 삭제
                if (profile.getAwards() != null && !profile.getAwards().isEmpty()) {
                    new ArrayList<>(profile.getAwards()).forEach(award -> {
                        award.setProfile(null); // 관계 해제
                        entityManager.remove(award);
                    });
                    profile.getAwards().clear();
                }
                if (profile.getCertifications() != null && !profile.getCertifications().isEmpty()) {
                    new ArrayList<>(profile.getCertifications()).forEach(cert -> {
                        cert.setProfile(null);
                        entityManager.remove(cert);
                    });
                    profile.getCertifications().clear();
                }
                if (profile.getExperiences() != null && !profile.getExperiences().isEmpty()) {
                    new ArrayList<>(profile.getExperiences()).forEach(exp -> {
                        exp.setProfile(null);
                        entityManager.remove(exp);
                    });
                    profile.getExperiences().clear();
                }
                // 삭제 작업을 즉시 반영
                entityManager.flush();
                log.debug("Cleared existing child entities for user: {}", userId);
            }

            // 이제 안전하게 수정 가능
            UserProfileMapper.apply(profile, request);
            
            // 최종 저장 (기존 엔티티는 merge, 새 엔티티는 이미 persist됨)
            entityManager.flush(); // 변경사항 반영
            log.debug("Successfully saved profile for user: {}", userId);
            
            // 엔티티를 다시 로드하여 최신 상태 보장
            UserProfileEntity saved = userProfileRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Failed to save profile"));
            
            return UserProfileMapper.toResponse(saved);
        } catch (Exception e) {
            log.error("Error saving profile for user: {}", userId, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public boolean isProfileComplete(Long userId) {
        return userProfileRepository.findById(userId)
                .map(profile -> {
                    // 필수 필드 체크: 대학교 정보가 있는지 확인
                    return profile.getUniversityName() != null && 
                           !profile.getUniversityName().isBlank() &&
                           profile.getUniversityMajor() != null && 
                           !profile.getUniversityMajor().isBlank() &&
                           profile.getUniversityGrade() != null;
                })
                .orElse(false);
    }
}
