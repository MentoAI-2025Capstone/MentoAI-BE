package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserEntity createUser(UserEntity user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + user.getEmail());
        }
        if (user.getAuthProvider() == null) {
            user.setAuthProvider(UserEntity.AuthProvider.GOOGLE);
        }
        if (!StringUtils.hasText(user.getProviderUserId())) {
            user.setProviderUserId(user.getEmail());
        }
        return userRepository.save(user);
    }

    public Optional<UserEntity> getUser(Long id) {
        return userRepository.findById(id);
    }

    public Optional<UserEntity> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public Optional<UserEntity> updateUser(Long id, UserEntity updatedUser) {
        return userRepository.findById(id)
            .map(existingUser -> {
                existingUser.setName(updatedUser.getName());
                existingUser.setNickname(updatedUser.getNickname());
                existingUser.setProfileImageUrl(updatedUser.getProfileImageUrl());
                return existingUser;
            });
    }

    @Transactional
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Transactional
    public UserEntity upsertOAuthUser(UserEntity.AuthProvider provider,
                                      String providerUserId,
                                      String email,
                                      String name,
                                      String nickname,
                                      String profileImageUrl) {
        return userRepository.findByAuthProviderAndProviderUserId(provider, providerUserId)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(name);
                    existing.setNickname(nickname);
                    existing.setProfileImageUrl(profileImageUrl);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .authProvider(provider)
                        .providerUserId(providerUserId)
                        .email(email)
                        .name(name)
                        .nickname(nickname)
                        .profileImageUrl(profileImageUrl)
                        .build()));
    }
}





