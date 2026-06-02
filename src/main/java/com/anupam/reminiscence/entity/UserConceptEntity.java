package com.anupam.reminiscence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_concept",
        schema = "retention",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "concept_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserConceptEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "concept_id", nullable = false)
    private UUID conceptId;

    @Column(name = "mastery_score", nullable = false)
    private Integer masteryScore;

    @Column(name = "current_interval_days", nullable = false)
    private Integer currentIntervalDays;

    @Column(name = "next_review_date", nullable = false)
    private LocalDate nextReviewDate;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "stability")
    private Double stability;
    // Memory stability in days. NULL / 0 before first review.

    @Column(name = "difficulty")
    private Double difficulty;
    // Concept difficulty [1..10]. NULL / 0 before first review.
}