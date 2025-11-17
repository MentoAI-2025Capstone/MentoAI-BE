package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.dto.UserProfileUpsertRequest;
import com.mentoai.mentoai.controller.mapper.UserProfileMapper;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserProfileEntity;
import com.mentoai.mentoai.repository.UserProfileRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    public UserProfileResponse getProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userProfileRepository.findById(userId)
                .map(UserProfileMapper::toResponse)
                .orElse(UserProfileMapper.empty(user));
    }

    @Transactional
    public UserProfileResponse upsertProfile(Long userId, UserProfileUpsertRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserProfileEntity profile = userProfileRepository.findById(userId)
                .orElseGet(() -> {
                    UserProfileEntity newProfile = new UserProfileEntity();
                    newProfile.setUserId(userId);
                    newProfile.setUser(user);
                    // 리스트 초기화 보장
                    if (newProfile.getInterestDomains() == null) {
                        newProfile.setInterestDomains(new ArrayList<>());
                    }
                    if (newProfile.getTechStack() == null) {
                        newProfile.setTechStack(new ArrayList<>());
                    }
                    if (newProfile.getAwards() == null) {
                        newProfile.setAwards(new ArrayList<>());
                    }
                    if (newProfile.getCertifications() == null) {
                        newProfile.setCertifications(new ArrayList<>());
                    }
                    if (newProfile.getExperiences() == null) {
                        newProfile.setExperiences(new ArrayList<>());
                    }
                    return newProfile;
                });

        UserProfileMapper.apply(profile, request);
        UserProfileEntity saved = userProfileRepository.save(profile);
        return UserProfileMapper.toResponse(saved);
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
