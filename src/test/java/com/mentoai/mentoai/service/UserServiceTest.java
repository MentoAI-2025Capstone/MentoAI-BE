package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setName("테스트 사용자");
        testUser.setEmail("test@example.com");
        testUser.setMajor("컴퓨터공학과");
        testUser.setGrade(3);
    }

    @Test
    @DisplayName("사용자 생성 테스트 - 성공")
    void createUser_Success() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // When
        UserEntity result = userService.createUser(testUser);

        // Then
        assertNotNull(result);
        assertEquals("테스트 사용자", result.getName());
        assertEquals("test@example.com", result.getEmail());
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("사용자 생성 테스트 - 실패 (중복 이메일)")
    void createUser_DuplicateEmail() {
        // Given
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(testUser);
        });
        
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("사용자 조회 테스트 - 성공")
    void getUser_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        Optional<UserEntity> result = userService.getUser(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("테스트 사용자", result.get().getName());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("사용자 조회 테스트 - 실패")
    void getUser_NotFound() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<UserEntity> result = userService.getUser(999L);

        // Then
        assertFalse(result.isPresent());
        verify(userRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("사용자 수정 테스트 - 성공")
    void updateUser_Success() {
        // Given
        UserEntity updatedUser = new UserEntity();
        updatedUser.setName("수정된 이름");
        updatedUser.setEmail("updated@example.com");
        updatedUser.setMajor("소프트웨어학과");
        updatedUser.setGrade(4);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // When
        Optional<UserEntity> result = userService.updateUser(1L, updatedUser);

        // Then
        assertTrue(result.isPresent());
        assertEquals("수정된 이름", result.get().getName());
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("사용자 삭제 테스트 - 성공")
    void deleteUser_Success() {
        // Given
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);

        // When
        boolean result = userService.deleteUser(1L);

        // Then
        assertTrue(result);
        verify(userRepository, times(1)).existsById(1L);
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("사용자 삭제 테스트 - 실패")
    void deleteUser_NotFound() {
        // Given
        when(userRepository.existsById(999L)).thenReturn(false);

        // When
        boolean result = userService.deleteUser(999L);

        // Then
        assertFalse(result);
        verify(userRepository, times(1)).existsById(999L);
        verify(userRepository, never()).deleteById(999L);
    }
}





