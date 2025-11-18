package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.UserInterestRepository;
import com.mentoai.mentoai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private UserInterestRepository userInterestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RecommendService recommendService;

    private ActivityEntity testActivity;
    private UserInterestEntity testInterest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testActivity = new ActivityEntity();
        testActivity.setId(1L);
        testActivity.setTitle("개발자 컨퍼런스");
        testActivity.setContent("최신 개발 트렌드");
        testActivity.setType(ActivityType.STUDY);
        
        testInterest = new UserInterestEntity();
        testInterest.setId(1L);
        testInterest.setUserId(1L);
        testInterest.setTagId(1L);
        testInterest.setScore(5);
    }

    @Test
    @DisplayName("사용자 맞춤 추천 테스트 - 성공")
    void getRecommendations_Success() {
        // Given
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userInterestRepository.findByUserIdOrderByScoreDesc(1L))
            .thenReturn(Arrays.asList(testInterest));
        
        Page<ActivityEntity> page = new PageImpl<>(Arrays.asList(testActivity));
        when(activityRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        // When
        List<ActivityEntity> results = recommendService.getRecommendations(1L, 10, null, null);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty());
        verify(userRepository, times(1)).existsById(1L);
    }

    @Test
    @DisplayName("사용자 맞춤 추천 테스트 - 사용자 없음")
    void getRecommendations_UserNotFound() {
        // Given
        when(userRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            recommendService.getRecommendations(999L, 10, null, null);
        });
    }

    @Test
    @DisplayName("의미 기반 검색 테스트 - 성공")
    void semanticSearch_Success() {
        // Given
        Page<ActivityEntity> page = new PageImpl<>(Arrays.asList(testActivity));
        when(activityRepository.findByFilters(anyString(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        // When
        List<ActivityEntity> results = recommendService.semanticSearch("개발", 5, null);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("의미 기반 검색 테스트 - 빈 검색어")
    void semanticSearch_EmptyQuery() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            recommendService.semanticSearch("", 5, null);
        });
    }

    @Test
    @DisplayName("인기 활동 조회 테스트")
    void getTrendingActivities_Success() {
        // Given
        Page<ActivityEntity> page = new PageImpl<>(Arrays.asList(testActivity));
        when(activityRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        // When
        List<ActivityEntity> results = recommendService.getTrendingActivities(10, null);

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("유사 활동 추천 테스트 - 성공")
    void getSimilarActivities_Success() {
        // Given
        when(activityRepository.findById(1L)).thenReturn(java.util.Optional.of(testActivity));
        
        Page<ActivityEntity> page = new PageImpl<>(Arrays.asList(testActivity));
        when(activityRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        // When
        List<ActivityEntity> results = recommendService.getSimilarActivities(1L, 5);

        // Then
        assertNotNull(results);
        verify(activityRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("유사 활동 추천 테스트 - 활동 없음")
    void getSimilarActivities_NotFound() {
        // Given
        when(activityRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            recommendService.getSimilarActivities(999L, 5);
        });
    }
}

















