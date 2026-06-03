package com.anupam.reminiscence.engine.impl;

import com.anupam.reminiscence.constants.Level;
import com.anupam.reminiscence.engine.AdvancedSchedulingEngine;
import com.anupam.reminiscence.engine.dto.EngineMetrics;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component("reminiscenceFsrsV5")
public class ReminiscenceFsrsV5 implements AdvancedSchedulingEngine {

    private static final double[] W = {
            0.402, 0.946, 2.408, 5.831,
            4.93, 0.94, 0.86, 0.01,
            1.54, 0.15, 0.27,
            1.33, 0.23, 0.98,
            2.19, 0.23, 0.33,
            0.11, 0.20, 2.01, 0.12, 0.32
    };

    private static final double FSRS_DECAY = -0.4;
    private static final double FSRS_FACTOR = Math.pow(0.9, 1.0 / FSRS_DECAY) - 1.0;

    @Override
    public String getEngineSignature() {
        return "Reminiscence FSRS v5.0 (Gamified Flagship Engine)";
    }

    @Override
    public double calculateRetrievability(double stability, double elapsedDays) {
        if (stability <= 0 || elapsedDays <= 0) return 1.0;
        return Math.pow(1.0 + FSRS_FACTOR * (elapsedDays / stability), FSRS_DECAY);
    }

    @Override
    public EngineMetrics compute(double stability, double difficulty, double elapsedDays, Level rating, int reviewCount) {
        double sanitizedElapsed = Math.max(0.001, elapsedDays);
        double retrievability = calculateRetrievability(stability, sanitizedElapsed);

        double nextStability;
        double nextDifficulty;

        if (reviewCount == 0) {
            nextStability = seedStability(rating);
            nextDifficulty = seedDifficulty(rating);
        } else if (rating == Level.AGAIN) {
            nextStability = calculateLapse(stability, difficulty, retrievability);
            nextDifficulty = shiftDifficulty(difficulty, rating);
        } else {
            nextStability = calculateRecall(stability, difficulty, retrievability, rating, sanitizedElapsed);
            nextDifficulty = shiftDifficulty(difficulty, rating);
        }

        nextStability = Math.max(0.1, Math.min(36500.0, nextStability));
        nextDifficulty = Math.max(1.0, Math.min(10.0, nextDifficulty));

        int fuzzedInterval = (sanitizedElapsed < 0.15) ? 1 : generateDynamicFuzz(nextStability);
        int masteryDelta = computeDynamicMastery(rating, nextDifficulty);

        return EngineMetrics.builder()
                .stability(nextStability)
                .difficulty(nextDifficulty)
                .intervalDays(fuzzedInterval)
                .masteryDelta(masteryDelta)
                .build();
    }

    private double calculateRecall(double S, double D, double R, Level rating, double elapsedDays) {
        double expFactor = Math.exp(W[8]) * (11.0 - D) * Math.pow(S, -W[9]) * (Math.exp(W[10] * (1.0 - R)) - 1.0);
        double modifier = switch (rating) {
            case HARD -> W[11];
            case GOOD -> 1.0;
            case EASY -> W[12];
            default -> 1.0;
        };

        double calculatedS = S * (1.0 + expFactor) * modifier;

        if (elapsedDays < 1.0) {
            calculatedS = S * (1.0 + (calculatedS - S) * W[13] * elapsedDays);
        }
        return calculatedS;
    }

    private double calculateLapse(double S, double D, double R) {
        double softLanding = W[14] * Math.pow(D, -W[15]) * Math.pow(Math.max(S, 1.0), W[16]) * Math.exp(W[17] * (1.0 - R));
        return Math.max(0.1, Math.min(softLanding, S * W[18]));
    }

    private double shiftDifficulty(double d, Level rating) {
        double ratingValue = switch (rating) { case AGAIN -> 1.0; case HARD -> 2.0; case GOOD -> 3.0; default -> 4.0; };
        double targetD = d - W[6] * (ratingValue - 3.0);
        return W[7] * W[4] + (1.0 - W[7]) * targetD;
    }

    private int generateDynamicFuzz(double stability) {
        int baseDays = (int) Math.max(1, Math.round(stability));
        if (baseDays <= 2) return baseDays;

        double varianceScale = Math.min(W[19], 1.0 / Math.pow(baseDays, W[20]));
        double currentVariance = ThreadLocalRandom.current().nextDouble(-varianceScale, varianceScale);

        int fuzzedDays = (int) Math.round(baseDays * (1.0 + currentVariance));
        return Math.max(2, Math.min(36500, fuzzedDays));
    }

    private int computeDynamicMastery(Level rating, double difficulty) {
        if (rating == Level.AGAIN) return -12;

        double difficultyWeight = difficulty / 5.0;
        double baselinePoints = switch (rating) { case EASY -> 7.5; case GOOD -> 4.0; default -> 1.5; };

        // Varied Rewards: Keeping outcomes slightly unpredictable maximizes dopamine response
        double dopamineBurstScalar = ThreadLocalRandom.current().nextDouble(0.92, 1.35);

        return Math.max(1, Math.min(25, (int) Math.round(baselinePoints * difficultyWeight * dopamineBurstScalar)));
    }

    private double seedStability(Level rating) {
        return switch (rating) { case AGAIN -> W[0]; case HARD -> W[1]; case GOOD -> W[2]; case EASY -> W[3]; };
    }

    private double seedDifficulty(Level rating) {
        return switch (rating) {
            case AGAIN -> W[4];
            case HARD  -> W[4] + W[5];
            case GOOD  -> W[4] + (W[5] * 2);
            case EASY  -> W[4] + (W[5] * 3);
        };
    }
}