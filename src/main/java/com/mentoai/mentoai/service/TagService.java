package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.entity.TagEntity.TagType;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {
    
    private final TagRepository tagRepository;
    
    public Page<TagEntity> getTags(String query, TagType type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return tagRepository.findByFilters(query, type, pageable);
    }
    
    @Transactional
    public TagEntity createTag(TagEntity tag) {
        // 중복 태그명 체크
        if (tagRepository.existsByName(tag.getName())) {
            throw new IllegalArgumentException("이미 존재하는 태그명입니다: " + tag.getName());
        }
        return tagRepository.save(tag);
    }
    
    public Optional<TagEntity> getTagByName(String name) {
        return tagRepository.findByName(name);
    }
    
    public List<TagEntity> getTagsByType(TagType type) {
        return tagRepository.findByType(type);
    }
    
    public List<TagEntity> getAllTags() {
        return tagRepository.findAll();
    }
}



