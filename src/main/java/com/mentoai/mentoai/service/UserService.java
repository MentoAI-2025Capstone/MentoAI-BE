package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    
    private final UserRepository userRepository;
    
    @Transactional
    public UserEntity createUser(UserEntity user) {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + user.getEmail());
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
                existingUser.setMajor(updatedUser.getMajor());
                existingUser.setGrade(updatedUser.getGrade());
                return userRepository.save(existingUser);
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
}



