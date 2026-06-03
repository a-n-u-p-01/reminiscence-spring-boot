package com.anupam.reminiscence.engine;

import com.anupam.reminiscence.constants.Level;
import com.anupam.reminiscence.engine.dto.EngineMetrics;

public interface AdvancedSchedulingEngine {
    EngineMetrics compute(double stability, double difficulty, double elapsedDays, Level rating, int reviewCount);
    double calculateRetrievability(double stability, double elapsedDays);
    String getEngineSignature();
}