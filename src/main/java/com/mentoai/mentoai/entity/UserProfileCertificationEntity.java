package com.mentoai.mentoai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "user_profile_certifications")
@Getter
@Setter
@NoArgsConstructor
public class UserProfileCertificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cert_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private UserProfileEntity profile;

    private String name;
    private String issuer;

    @Column(name = "score_or_level")
    private String scoreOrLevel;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(name = "credential_id")
    private String credentialId;

    private String url;
}




