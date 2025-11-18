package com.mentoai.mentoai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfileEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private UserEntity user;

    @Column(name = "university_name")
    private String universityName;

    @Column(name = "university_grade")
    private Integer universityGrade;

    @Column(name = "university_major")
    private String universityMajor;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "user_profile_interest_domains", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "domain_name")
    private List<String> interestDomains = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_profile_skills", joinColumns = @JoinColumn(name = "user_id"))
    private List<UserProfileSkill> techStack = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProfileAwardEntity> awards = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProfileCertificationEntity> certifications = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserProfileExperienceEntity> experiences = new ArrayList<>();
}

