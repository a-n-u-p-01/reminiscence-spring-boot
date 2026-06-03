package com.anupam.reminiscence.engine.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EngineMetrics {
    double stability;
    double difficulty;
    int intervalDays;
    int masteryDelta;
}