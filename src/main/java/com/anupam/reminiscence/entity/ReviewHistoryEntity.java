package com.anupam.reminiscence.entity;

import com.anupam.reminiscence.constants.Level;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_history", schema = "retention")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewHistoryEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "concept_id", nullable = false)
    private UUID conceptId;

    @Column(nullable = false)
    private Level quality;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "stability_at_review")
    private Double stabilityAtReview;

    @Column(name = "difficulty_at_review")
    private Double difficultyAtReview;

    @Column(name = "retrievability_at_review")
    private Double retrievabilityAtReview;
}