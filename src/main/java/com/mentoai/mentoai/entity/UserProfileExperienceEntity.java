package com.mentoai.mentoai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profile_experiences")
@Getter
@Setter
@NoArgsConstructor
public class UserProfileExperienceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exp_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private UserProfileEntity profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ExperienceType type;

    private String title;
    private String organization;
    private String role;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_current")
    private Boolean current;

    private String description;
    private String url;

    @ElementCollection
    @CollectionTable(name = "user_profile_experience_skills", joinColumns = @JoinColumn(name = "exp_id"))
    @Column(name = "skill_name")
    private List<String> techStack = new ArrayList<>();
}




