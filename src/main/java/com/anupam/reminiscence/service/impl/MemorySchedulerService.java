package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.constants.Level;
import com.anupam.reminiscence.entity.ReviewHistoryEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MemorySchedulerService {

    private final UserConceptRepo userConceptRepo;
    private final ReviewHistoryRepo reviewHistoryRepo;
    private final UserRepository userRepository;

    // ── FSRS v4 POWER-LAW DECAY CONSTANTS ────────────────────────
    private static final double FSRS_DECAY = -0.5;
    private static final double FSRS_FACTOR = 19.0 / 81.0;

    // ── TUNED BRAIN METRIC WEIGHTS ───────────────────────────────
    private static final double W_RECALL = 0.09;
    private static final double W_RETRIEVABILITY = 1.2;
    private static final double S_DECELERATION_EXP = -0.2;

    // ── LAPSE (FAILURE) METRICS ──────────────────────────────────
    private static final double W_FAIL_BASE = 0.45;
    private static final double W_FAIL_DIFF = 0.25;
    private static final double W_FAIL_STAB = 0.25;
    private static final double W_FAIL_R = 0.80;

    // ── SAFETY BOUNDS ────────────────────────────────────────────
    private static final double MIN_STABILITY = 0.1;
    private static final double MAX_STABILITY = 36500.0;
    private static final double MIN_DIFFICULTY = 1.0;
    private static final double MAX_DIFFICULTY = 10.0;
    private static final int MAX_INTERVAL_DAYS = 365;

    // ── BOOTSTRAP SEEDS ──────────────────────────────────────────
    private static final double INIT_STAB_AGAIN = 0.5;
    private static final double INIT_STAB_HARD  = 1.0;
    private static final double INIT_STAB_GOOD  = 2.0;
    private static final double INIT_STAB_EASY  = 4.0;

    private static final double INIT_DIFF_EASY  = 3.0;
    private static final double INIT_DIFF_GOOD  = 5.0;
    private static final double INIT_DIFF_HARD  = 7.0;
    private static final double INIT_DIFF_AGAIN = 9.0;

    @Transactional(rollbackFor = Exception.class)
    public void reviewConcept(UUID userId, UUID userConceptId, Level rating) {

        UserConceptEntity concept = userConceptRepo
                .findByIdAndUserId(userConceptId, userId)
                .orElseThrow(() -> new RuntimeException("Review target concept card not found"));

        // Global absolute tracking baseline
        Instant nowUTC = Instant.now();
        ZoneId userZone = resolveTimezone(userId);
        LocalDate todayLocal = LocalDate.ofInstant(nowUTC, userZone);

        int reviewCount = concept.getReviewCount();

        double stability = coalesce(concept.getStability(), 0.0);
        double difficulty = coalesce(concept.getDifficulty(), 5.0);

        // 1. Bug-Free Temporal Tracking between Instant objects
        double elapsedDays = 0.0;
        if (concept.getLastReviewedAt() != null) {
            long secondsParam = ChronoUnit.SECONDS.between(concept.getLastReviewedAt(), nowUTC);
            elapsedDays = Math.max(0.01, secondsParam / 86400.0);
        }

        double currentR = (stability > 0 && elapsedDays > 0)
                ? calculateRetrievability(elapsedDays, stability)
                : 1.0;

        double newStability;
        double newDifficulty;
        int newMastery = coalesce(concept.getMasteryScore(), 0);
        int newFailureCount = coalesce(concept.getFailureCount(), 0);

        if (reviewCount == 0) {
            newStability = initStability(rating);
            newDifficulty = initDifficulty(rating);
            newMastery = initMastery(rating);
        } else if (rating == Level.AGAIN) {
            newStability = stabilityAfterLapse(stability, difficulty, currentR);
            newDifficulty = updateDifficulty(difficulty, rating);
            newMastery = Math.max(0, newMastery - 12);
            newFailureCount++;
        } else {
            newStability = stabilityAfterRecall(stability, difficulty, currentR, rating);
            newDifficulty = updateDifficulty(difficulty, rating);

            if (rating == Level.EASY) newMastery = Math.min(100, newMastery + 8);
            else if (rating == Level.GOOD) newMastery = Math.min(100, newMastery + 4);
            else newMastery = Math.min(100, newMastery + 1);
        }

        newStability = Math.max(MIN_STABILITY, Math.min(MAX_STABILITY, newStability));
        newDifficulty = Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, newDifficulty));

        // 2. Linear Interval Extraction with Fuzzing
        int nextInterval = calculateFuzzedInterval(newStability);

        concept.setStability(newStability);
        concept.setDifficulty(newDifficulty);
        concept.setCurrentIntervalDays(nextInterval);
        concept.setMasteryScore(newMastery);
        concept.setFailureCount(newFailureCount);
        concept.setReviewCount(reviewCount + 1);
        concept.setLastReviewedAt(nowUTC); // Direct assignment to an Instant field
        concept.setNextReviewDate(todayLocal.plusDays(nextInterval));
        concept.setUpdatedAt(nowUTC);

        userConceptRepo.save(concept);

        // 3. Clear History Audit Trace mapped with uniform temporal instants
        ReviewHistoryEntity log = ReviewHistoryEntity.builder()
                .userId(userId)
                .conceptId(concept.getConceptId())
                .quality(rating)
                .intervalDays(nextInterval)
                .stabilityAtReview(newStability)
                .difficultyAtReview(newDifficulty)
                .retrievabilityAtReview(currentR)
                .reviewedAt(nowUTC)
                .createdAt(nowUTC)
                .build();
        reviewHistoryRepo.save(log);
    }

    private double calculateRetrievability(double elapsedDays, double stability) {
        if (stability <= 0) return 0.0;
        return Math.pow(1.0 + FSRS_FACTOR * elapsedDays / stability, FSRS_DECAY);
    }

    private int calculateFuzzedInterval(double stability) {
        int baseInterval = (int) Math.max(1, Math.round(stability));

        if (baseInterval <= 2) {
            return baseInterval;
        }

        double fuzzFactor = ThreadLocalRandom.current().nextDouble(0.05, 0.15);
        int fuzzRange = (int) Math.max(1, Math.round(baseInterval * fuzzFactor));

        boolean nativeDirection = ThreadLocalRandom.current().nextBoolean();
        int finalInterval = nativeDirection ? baseInterval + fuzzRange : baseInterval - fuzzRange;

        return Math.max(2, Math.min(MAX_INTERVAL_DAYS, finalInterval));
    }

    private double stabilityAfterRecall(double S, double D, double R, Level rating) {
        double diffFactor = 11.0 - D;
        double overdueBonus = 1.0 + W_RETRIEVABILITY * (1.0 - R);
        double stabilityDeceleration = Math.pow(Math.max(S, 1.0), S_DECELERATION_EXP);
        double growthFactor = 1.0 + (W_RECALL * diffFactor * stabilityDeceleration * overdueBonus);

        double ratingMultiplier = switch (rating) {
            case EASY -> 1.35;
            case GOOD -> 1.00;
            case HARD -> 0.75;
            default -> 1.00;
        };

        return S * growthFactor * ratingMultiplier;
    }

    private double stabilityAfterLapse(double S, double D, double R) {
        double newS = W_FAIL_BASE
                * Math.pow(D, -W_FAIL_DIFF)
                * Math.pow(Math.max(S, 1.0), W_FAIL_STAB)
                * Math.exp(W_FAIL_R * (1.0 - R));

        return Math.max(MIN_STABILITY, Math.min(newS, S * 0.4));
    }

    private double updateDifficulty(double d, Level rating) {
        double delta = switch (rating) {
            case EASY  -> -0.22;
            case GOOD  -> -0.04;
            case HARD  ->  0.18;
            case AGAIN ->  0.40;
        };
        return d + delta;
    }

    private double initStability(Level rating) {
        return switch (rating) {
            case EASY  -> INIT_STAB_EASY;
            case GOOD  -> INIT_STAB_GOOD;
            case HARD  -> INIT_STAB_HARD;
            case AGAIN -> INIT_STAB_AGAIN;
        };
    }

    private double initDifficulty(Level rating) {
        return switch (rating) {
            case EASY  -> INIT_DIFF_EASY;
            case GOOD  -> INIT_DIFF_GOOD;
            case HARD  -> INIT_DIFF_HARD;
            case AGAIN -> INIT_DIFF_AGAIN;
        };
    }

    private int initMastery(Level rating) {
        return switch (rating) {
            case EASY  -> 60;
            case GOOD  -> 40;
            case HARD  -> 20;
            case AGAIN -> 0;
        };
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