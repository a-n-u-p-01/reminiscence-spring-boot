package com.anupam.reminiscence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pending_registration", schema = "retention")
public class PendingRegistrationEntity {

    @Id
    private String email;

    private String fullName;

    private String passwordHash;

    private String otp;

    private LocalDateTime expiryTime;

    private LocalDateTime createdAt;
}