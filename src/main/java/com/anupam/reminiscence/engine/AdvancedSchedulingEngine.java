package com.anupam.reminiscence.engine;

import com.anupam.reminiscence.constants.RecallRating;
import com.anupam.reminiscence.engine.dto.EngineMetrics;

public interface AdvancedSchedulingEngine {
    EngineMetrics compute(double stability, double difficulty, double elapsedDays, RecallRating rating, int reviewCount);
    double calculateRetrievability(double stability, double elapsedDays);
    String getEngineSignature();
}