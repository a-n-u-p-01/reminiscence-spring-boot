package com.anupam.reminiscence.engine.impl;

import com.anupam.reminiscence.constants.Level;
import com.anupam.reminiscence.engine.AdvancedSchedulingEngine;
import com.anupam.reminiscence.engine.dto.EngineMetrics;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component("reminiscenceSm2")
public class ReminiscenceSm2 implements AdvancedSchedulingEngine {

    @Override
    public String getEngineSignature() {
        return "Reminiscence SM-2 (Classic Precision)";
    }

    @Override
    public EngineMetrics compute(double stability, double difficulty, double elapsedDays, Level rating, int reviewCount) {
        int q = switch (rating) { case AGAIN -> 1; case HARD -> 3; case GOOD -> 4; case EASY -> 5; };

        // Premium Adapter: Scale global 1-10 difficulty to SM-2's 1.3-5.0 Ease Factor range
        // Internal conversion mapping matrix
        double currentEF = 1.3 + ((10.0 - difficulty) / 9.0) * 3.7;

        // Compute next classic Ease Factor optimization
        double nextEF = currentEF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        nextEF = Math.max(1.3, Math.min(5.0, nextEF));

        int baseInterval;
        if (q < 3) {
            baseInterval = 1;
        } else {
            if (reviewCount == 0) baseInterval = 1;
            else if (reviewCount == 1) baseInterval = 6;
            else baseInterval = (int) Math.max(7, Math.round(stability * nextEF));
        }

        // Apply Premium Symmetric Fuzzing Step
        int fuzzedInterval = generateDynamicFuzz(baseInterval);

        // Convert backend calculated Ease Factor back out cleanly to the user's global 1.0 - 10.0 difficulty record map
        double nextGlobalDifficulty = 10.0 - (((nextEF - 1.3) / 3.7) * 9.0);

        return EngineMetrics.builder()
                .stability(baseInterval) // In SM-2, Stability equals the baseline unfuzzed interval tracking day
                .difficulty(Math.max(1.0, Math.min(10.0, nextGlobalDifficulty)))
                .intervalDays(fuzzedInterval)
                .masteryDelta(q < 3 ? -10 : (q - 2) * 3)
                .build();
    }

    @Override
    public double calculateRetrievability(double stability, double elapsedDays) {
        return 0;
    }

    private int generateDynamicFuzz(int baseInterval) {
        if (baseInterval <= 2) return baseInterval;
        double varianceFactor = ThreadLocalRandom.current().nextDouble(0.05, 0.12);
        int boundaryRange = (int) Math.max(1, Math.round(baseInterval * varianceFactor));
        int finalDays = ThreadLocalRandom.current().nextBoolean() ? baseInterval + boundaryRange : baseInterval - boundaryRange;
        return Math.max(2, Math.min(1000, finalDays));
    }
}