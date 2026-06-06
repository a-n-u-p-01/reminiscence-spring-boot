package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.constants.RecallRating;
import com.anupam.reminiscence.entity.ReviewHistoryEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.engine.AdvancedSchedulingEngine;
import com.anupam.reminiscence.engine.dto.EngineMetrics;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.repo.ReviewHistoryRepo;
import com.anupam.reminiscence.repo.UserConceptRepo;
import com.anupam.reminiscence.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemorySchedulerService {

    private final UserConceptRepo userConceptRepo;
    private final ReviewHistoryRepo reviewHistoryRepo;
    private final UserRepository userRepository;

    // Dynamic engine registry handles poly-algorithmic strategies safely
    private final Map<String, AdvancedSchedulingEngine> engineRegistry;

    private static final long DOUBLE_SUBMIT_FREEZE_SECONDS = 4;
    private static final double BACKLOG_DAMPENER_MAX_MULTIPLIER = 1.20;

    @Transactional(rollbackFor = Exception.class)
    public void reviewConcept(UUID userId, UUID userConceptId, RecallRating rating) {

        UserConceptEntity concept = userConceptRepo
                .findByIdAndUserId(userConceptId, userId)
                .orElseThrow(() -> new RuntimeException("Review target concept card not found"));

        Instant nowUTC = Instant.now();
        ZoneId userZone = resolveTimezone(userId);
        LocalDate todayLocal = LocalDate.ofInstant(nowUTC, userZone);

        int reviewCount = coalesce(concept.getReviewCount(), 0);

        // Follows the specified initial placeholder metrics (Stability 1.0, Difficulty 5.0)
        double currentStability = (reviewCount == 0) ? 1.0 : coalesce(concept.getStability(), 1.0);
        double currentDifficulty = (reviewCount == 0) ? 5.0 : coalesce(concept.getDifficulty(), 5.0);

        double elapsedDays = 0.0;
        if (concept.getLastReviewedAt() != null) {
            long secondsDelta = ChronoUnit.SECONDS.between(concept.getLastReviewedAt(), nowUTC);

            // Mitigates rapid multi-click taps over high-latency networks
            if (secondsDelta < DOUBLE_SUBMIT_FREEZE_SECONDS && reviewCount > 0) {
                return;
            }
            elapsedDays = Math.max(0.0, secondsDelta / 86400.0);
        }

        // True Backlog Dampening Solution for overdue elements
        boolean isOverdueBacklog = false;
        if (reviewCount > 0 && currentStability > 0 && elapsedDays > (currentStability * 1.15)) {
            isOverdueBacklog = true;
            if (rating == RecallRating.RECALLED || rating == RecallRating.FLUENT) {
                elapsedDays = Math.min(elapsedDays, currentStability * BACKLOG_DAMPENER_MAX_MULTIPLIER);
            } else if (rating == RecallRating.PARTIAL) {
                elapsedDays = currentStability;
            }
        }

        String activeEngineKey = userRepository.findById(userId)
                .map(UserEntity::getAlgorithmPreference)
                .orElse("reminiscenceFsrsV5");

        AdvancedSchedulingEngine runningEngine = engineRegistry.getOrDefault(
                activeEngineKey,
                engineRegistry.get("reminiscenceFsrsV5")
        );

        // Uses the active engine implementation to calculate retrievability indices
        double currentR = runningEngine.calculateRetrievability(currentStability, elapsedDays);

        EngineMetrics metrics = runningEngine.compute(
                currentStability,
                currentDifficulty,
                elapsedDays,
                rating,
                reviewCount
        );

        int newFailureCount = coalesce(concept.getFailureCount(), 0);
        if (rating == RecallRating.FORGOT) {
            newFailureCount++;
        }

        int updatedMastery = Math.max(0, Math.min(100, coalesce(concept.getMasteryScore(), 0) + metrics.getMasteryDelta()));

        // Aligning Stability parameters with the next review date interval
        concept.setStability(metrics.getStability());
        concept.setDifficulty(metrics.getDifficulty());
        concept.setCurrentIntervalDays(metrics.getIntervalDays());
        concept.setMasteryScore(updatedMastery);
        concept.setFailureCount(newFailureCount);
        concept.setReviewCount(reviewCount + 1);
        concept.setLastReviewedAt(nowUTC);

        // If an item is significantly overdue, its schedule calculates from today
        if (isOverdueBacklog && (rating == RecallRating.RECALLED || rating == RecallRating.FLUENT)) {
            concept.setNextReviewDate(todayLocal.plusDays(metrics.getIntervalDays()));
        } else {
            concept.setNextReviewDate(todayLocal.plusDays(metrics.getIntervalDays()));
        }

        concept.setUpdatedAt(nowUTC);
        userConceptRepo.save(concept);

        ReviewHistoryEntity log = ReviewHistoryEntity.builder()
                .userId(userId)
                .conceptId(concept.getConcept().getId())
                .quality(rating)
                .intervalDays(metrics.getIntervalDays())
                .stabilityAtReview(metrics.getStability())
                .difficultyAtReview(metrics.getDifficulty())
                .retrievabilityAtReview(currentR)
                .reviewedAt(nowUTC)
                .createdAt(nowUTC)
                .build();
        reviewHistoryRepo.save(log);
    }

    private ZoneId resolveTimezone(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> ZoneId.of(u.getTimezone()))
                .orElse(ZoneId.of("UTC"));
    }

    private static double coalesce(Double value, double fallback) {
        return (value == null || value == 0.0) ? fallback : value;
    }

    private static int coalesce(Integer value, int fallback) {
        return (value == null) ? fallback : value;
    }
}