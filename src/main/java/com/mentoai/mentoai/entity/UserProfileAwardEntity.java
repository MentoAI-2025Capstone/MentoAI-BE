package com.mentoai.mentoai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "user_profile_awards")
@Getter
@Setter
@NoArgsConstructor
public class UserProfileAwardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "award_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private UserProfileEntity profile;

    private String title;
    private String issuer;

    @Column(name = "date")
    private LocalDate date;

    private String description;
    private String url;
}




