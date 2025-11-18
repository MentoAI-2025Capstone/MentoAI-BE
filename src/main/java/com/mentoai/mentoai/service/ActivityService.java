package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityDateUpsertRequest;
import com.mentoai.mentoai.controller.dto.ActivityUpsertRequest;
import com.mentoai.mentoai.controller.dto.AttachmentUpsertRequest;
import com.mentoai.mentoai.entity.*;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import com.mentoai.mentoai.repository.UserInterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {
    
    private final ActivityRepository activityRepository;
    private final TagRepository tagRepository;
    private final NotificationService notificationService;
    private final RecommendService recommendService;
    private final UserInterestRepository userInterestRepository;
    
    public Page<ActivityEntity> getActivities(
            Long userId,
            String query,
            ActivityType type,
            List<String> tagNames,
            Boolean isCampus,
            ActivityStatus status,
            LocalDate deadlineBefore,
            int page,
            int size,
            String sort,
            String direction) {

        Sort.Direction sortDirection;
        try {
            if (direction == null || direction.isBlank()) {
                sortDirection = Sort.Direction.DESC;
            } else {
                sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            sortDirection = Sort.Direction.DESC;
        }
        Sort sortObj = Sort.by(sortDirection, sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        // deadlineBefore 필터는 일시적으로 비활성화 (PostgreSQL 타입 추론 문제)
        // TODO: 추후 필요시 별도 쿼리 메서드로 구현

        // userId가 제공되면 사용자 맞춤 추천 적용
        if (userId != null) {
            return getPersonalizedActivities(userId, query, type, tagNames, isCampus, status, pageable);
        }

        // 기존 로직 (일반 조회)
        return activityRepository.search(
                query,
                type,
                (tagNames == null || tagNames.isEmpty()) ? null : tagNames,
                isCampus,
                status,
                null, // deadlineBefore 필터 비활성화
                ActivityDateEntity.DateType.APPLY_END,
                pageable
        );
    }
    
    /**
     * 사용자 맞춤 활동 목록 조회
     */
    private Page<ActivityEntity> getPersonalizedActivities(
            Long userId,
            String query,
            ActivityType type,
            List<String> tagNames,
            Boolean isCampus,
            ActivityStatus status,
            Pageable pageable) {
        
        // 사용자 관심사 조회
        List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userId);
        
        if (userInterests.isEmpty()) {
            // 관심사가 없으면 일반 조회로 fallback
            return activityRepository.search(
                    query,
                    type,
                    (tagNames == null || tagNames.isEmpty()) ? null : tagNames,
                    isCampus,
                    status,
                    null,
                    ActivityDateEntity.DateType.APPLY_END,
                    pageable
            );
        }
        
        // 먼저 필터 조건으로 활동 조회 (더 많은 결과 가져오기)
        Page<ActivityEntity> filteredActivities = activityRepository.search(
                query,
                type,
                (tagNames == null || tagNames.isEmpty()) ? null : tagNames,
                isCampus,
                status,
                null,
                ActivityDateEntity.DateType.APPLY_END,
                Pageable.unpaged() // 모든 결과 가져오기
        );
        
        // 사용자 관심사 태그 ID 목록
        List<Long> userInterestTagIds = userInterests.stream()
                .map(UserInterestEntity::getTagId)
                .collect(Collectors.toList());
        
        // 활동별 점수 계산 및 정렬
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        for (ActivityEntity activity : filteredActivities.getContent()) {
            double score = 0.0;
            
            // 활동의 태그와 사용자 관심사 매칭
            if (activity.getActivityTags() != null && !activity.getActivityTags().isEmpty()) {
                for (var activityTag : activity.getActivityTags()) {
                    if (userInterestTagIds.contains(activityTag.getTag().getId())) {
                        // 관심사 점수에 따라 가중치 적용 (RecommendService와 동일한 로직)
                        UserInterestEntity matchingInterest = userInterests.stream()
                                .filter(ui -> ui.getTagId().equals(activityTag.getTag().getId()))
                                .findFirst()
                                .orElse(null);
                        if (matchingInterest != null) {
                            score += matchingInterest.getScore() * 10.0;
                        }
                    }
                }
            }
            
            // 활동 유형 보너스
            if (activity.getType() == ActivityType.STUDY) {
                score += 5.0;
            } else if (activity.getType() == ActivityType.CONTEST) {
                score += 3.0;
            }
            
            // 캠퍼스 활동 가중치
            if (activity.getIsCampus() != null && activity.getIsCampus()) {
                score += 2.0;
            }
            
            if (score > 0) {
                activityScores.put(activity, score);
            }
        }
        
        // 점수 순으로 정렬
        List<ActivityEntity> personalizedList = activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // 점수가 0인 활동도 추가 (점수 순 정렬 후)
        List<ActivityEntity> zeroScoreActivities = filteredActivities.getContent().stream()
                .filter(a -> !activityScores.containsKey(a))
                .collect(Collectors.toList());
        personalizedList.addAll(zeroScoreActivities);
        
        // 페이지네이션 적용
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), personalizedList.size());
        List<ActivityEntity> pagedList = start < personalizedList.size() 
                ? personalizedList.subList(start, end) 
                : List.of();
        
        return new PageImpl<>(pagedList, pageable, personalizedList.size());
    }
    
    @Transactional
    public ActivityEntity createActivity(ActivityUpsertRequest request) {
        ActivityEntity activity = new ActivityEntity();
        applyUpsert(activity, request);

        ActivityEntity savedActivity = activityRepository.save(activity);
        notificationService.createNewActivityNotification(savedActivity);
        return savedActivity;
    }
    
    public Optional<ActivityEntity> getActivity(Long id) {
        return activityRepository.findById(id);
    }
    
    @Transactional
    public Optional<ActivityEntity> updateActivity(Long id, ActivityUpsertRequest request) {
        return activityRepository.findById(id)
            .map(existingActivity -> {
                applyUpsert(existingActivity, request);
                return activityRepository.save(existingActivity);
            });
    }
    
    @Transactional
    public boolean deleteActivity(Long id) {
        if (activityRepository.existsById(id)) {
            activityRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    public List<ActivityEntity> getActiveActivities() {
        return activityRepository.findByStatus(ActivityStatus.OPEN);
    }
    
    public List<ActivityEntity> getCampusActivities(Boolean isCampus) {
        return activityRepository.findByIsCampus(isCampus);
    }

    @Transactional(readOnly = true)
    public List<AttachmentEntity> getAttachments(Long activityId) {
        return activityRepository.findById(activityId)
                .map(activity -> {
                    List<AttachmentEntity> attachments = activity.getAttachments();
                    return attachments == null
                            ? Collections.<AttachmentEntity>emptyList()
                            : List.copyOf(attachments);
                })
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId));
    }

    @Transactional
    public AttachmentEntity addAttachment(Long activityId, AttachmentUpsertRequest request) {
        ActivityEntity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId));

        if (activity.getAttachments() == null) {
            activity.setAttachments(new ArrayList<>());
        }

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setActivity(activity);
        attachment.setFileType(parseEnum(
                request.fileType(),
                AttachmentEntity.FileType::valueOf,
                AttachmentEntity.FileType.class));
        attachment.setFileUrl(request.fileUrl());
        attachment.setOcrText(request.ocrText());

        activity.getAttachments().add(attachment);
        activityRepository.saveAndFlush(activity);
        return attachment;
    }

    private void applyUpsert(ActivityEntity activity, ActivityUpsertRequest request) {
        activity.setTitle(request.title());
        activity.setSummary(request.summary());
        activity.setContent(request.content());
        activity.setType(parseEnum(request.type(), ActivityType::valueOf, ActivityType.class));
        activity.setOrganizer(request.organizer());
        activity.setLocation(request.location());
        activity.setUrl(request.url());
        activity.setIsCampus(request.isCampus() != null ? request.isCampus() : Boolean.FALSE);
        activity.setStatus(parseEnumOrDefault(request.status(), ActivityStatus::valueOf, ActivityStatus.OPEN));
        activity.setPublishedAt(request.publishedAt());

        syncActivityDates(activity, request.dates());
        syncActivityTags(activity, request.tags());
    }

    private void syncActivityDates(ActivityEntity activity, List<ActivityDateUpsertRequest> dateRequests) {
        if (activity.getDates() == null) {
            activity.setDates(new ArrayList<>());
        } else {
            activity.getDates().clear();
        }
        if (dateRequests == null) {
            return;
        }
        for (ActivityDateUpsertRequest dateRequest : dateRequests) {
            ActivityDateEntity dateEntity = new ActivityDateEntity();
            dateEntity.setActivity(activity);
            dateEntity.setDateType(parseEnum(
                    dateRequest.dateType(),
                    ActivityDateEntity.DateType::valueOf,
                    ActivityDateEntity.DateType.class));
            dateEntity.setDateValue(dateRequest.dateValue());
            activity.getDates().add(dateEntity);
        }
    }

    private void syncActivityTags(ActivityEntity activity, List<String> tagNames) {
        if (activity.getActivityTags() == null) {
            activity.setActivityTags(new ArrayList<>());
        } else {
            activity.getActivityTags().clear();
        }
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        List<String> distinctNames = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (distinctNames.isEmpty()) {
            return;
        }

        List<TagEntity> tags = tagRepository.findByNameIn(distinctNames);
        Set<String> foundNames = tags.stream().map(TagEntity::getName).collect(Collectors.toSet());
        List<String> missing = distinctNames.stream()
                .filter(name -> !foundNames.contains(name))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 태그입니다: " + String.join(", ", missing));
        }

        for (TagEntity tag : tags) {
            ActivityTagEntity activityTag = new ActivityTagEntity();
            activityTag.setActivity(activity);
            activityTag.setTag(tag);
            activity.getActivityTags().add(activityTag);
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, java.util.function.Function<String, E> parser, Class<E> enumClass) {
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            String allowed = String.join(", ", enumNames(enumClass));
            throw new IllegalArgumentException("허용되지 않는 값입니다. value=" + value + ", allowed=" + allowed);
        }
    }

    private <E extends Enum<E>> E parseEnumOrDefault(String value, java.util.function.Function<String, E> parser, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return parseEnum(value, parser, defaultValue.getDeclaringClass());
    }

    private <E extends Enum<E>> List<String> enumNames(Class<E> enumClass) {
        return java.util.Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .toList();
    }
}
