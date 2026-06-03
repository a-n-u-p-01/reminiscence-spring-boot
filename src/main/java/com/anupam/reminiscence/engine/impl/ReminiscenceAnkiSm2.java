package com.anupam.reminiscence.engine.impl;

import com.anupam.reminiscence.constants.Level;
import com.anupam.reminiscence.engine.AdvancedSchedulingEngine;
import com.anupam.reminiscence.engine.dto.EngineMetrics;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component("reminiscenceAnkiSm2")
public class ReminiscenceAnkiSm2 implements AdvancedSchedulingEngine {

    @Override
    public String getEngineSignature() {
        return "Reminiscence Anki-SM2 (Legacy Hybrid)";
    }

    @Override
    public EngineMetrics compute(double stability, double difficulty, double elapsedDays, Level rating, int reviewCount) {
        // Premium Adapter: Map global 1-10 scale down to legacy Ease Factor footprint
        double currentEF = 1.3 + ((10.0 - difficulty) / 9.0) * 3.7;
        double nextEF = currentEF;

        int baseInterval;
        double effectiveDays = Math.max(1.0, elapsedDays);

        switch (rating) {
            case AGAIN -> {
                nextEF = Math.max(1.3, currentEF - 0.20);
                baseInterval = 1;
            }
            case HARD -> {
                nextEF = Math.max(1.3, currentEF - 0.15);
                baseInterval = (int) Math.max(1, Math.round(effectiveDays * 1.20));
            }
            case GOOD -> {
                baseInterval = (reviewCount == 0) ? 1 : (reviewCount == 1) ? 4 : (int) Math.round(effectiveDays * currentEF);
            }
            case EASY -> {
                nextEF = Math.min(5.0, currentEF + 0.15);
                baseInterval = (int) Math.round(effectiveDays * currentEF * 1.30);
            }
            default -> baseInterval = 1;
        }

        int fuzzedInterval = generateDynamicFuzz(baseInterval);
        double nextGlobalDifficulty = 10.0 - (((nextEF - 1.3) / 3.7) * 9.0);

        return EngineMetrics.builder()
                .stability(baseInterval)
                .difficulty(Math.max(1.0, Math.min(10.0, nextGlobalDifficulty)))
                .intervalDays(fuzzedInterval)
                .masteryDelta(rating == Level.AGAIN ? -15 : rating == Level.EASY ? 10 : 5)
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
        return Math.max(1, Math.min(1000, finalDays));
    }
}