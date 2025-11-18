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

            // 기존 프로필 존재 여부 확인
            boolean exists = userProfileRepository.existsById(userId);
            
            UserProfileEntity profile;
            
            if (!exists) {
                // 새로 생성된 경우: 먼저 빈 엔티티를 저장
                profile = new UserProfileEntity();
                profile.setUserId(userId);
                profile.setUser(user);
                profile.setInterestDomains(new ArrayList<>());
                profile.setTechStack(new ArrayList<>());
                profile.setAwards(new ArrayList<>());
                profile.setCertifications(new ArrayList<>());
                profile.setExperiences(new ArrayList<>());
                
                // 먼저 저장하여 관리 상태로 만듦
                profile = userProfileRepository.saveAndFlush(profile);
                log.debug("Created new profile for user: {}", userId);
            } else {
                // 기존 프로필 로드
                profile = userProfileRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + userId));
                
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
                    // 컬렉션을 복사하여 삭제 (원본 컬렉션을 수정하면서 반복하면 안 됨)
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
            
            // 최종 저장
            UserProfileEntity saved = userProfileRepository.saveAndFlush(profile);
            log.debug("Successfully saved profile for user: {}", userId);
            
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
