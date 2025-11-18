package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityDateUpsertRequest;
import com.mentoai.mentoai.controller.dto.ActivityUpsertRequest;
import com.mentoai.mentoai.controller.dto.AttachmentUpsertRequest;
import com.mentoai.mentoai.entity.*;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    
    public Page<ActivityEntity> getActivities(
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

        // PostgreSQL 타입 추론 문제를 피하기 위해 null 대신 MAX 값을 사용
        LocalDateTime deadlineDateTime = deadlineBefore != null 
                ? deadlineBefore.atTime(LocalTime.MAX)
                : LocalDateTime.MAX;

        return activityRepository.search(
                query,
                type,
                (tagNames == null || tagNames.isEmpty()) ? null : tagNames,
                isCampus,
                status,
                deadlineDateTime,
                ActivityDateEntity.DateType.APPLY_END,
                pageable
        );
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
