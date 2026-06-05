package com.anupam.reminiscence.service;

import com.anupam.reminiscence.dto.ConceptExplorerResponse;
import com.anupam.reminiscence.dto.DashboardDTOs.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public interface DashboardService {
    MetricsResponse getDashboardMetrics(UUID userId);
    Map<String, Integer> getMatrixHeatmap(UUID userId);
    ActivityDetailsResponse getDailyActivityDetails(UUID userId, LocalDate date);
    ConceptExplorerResponse getConceptsExplorerList(UUID userId, String search, int page, boolean sortNewest);
}