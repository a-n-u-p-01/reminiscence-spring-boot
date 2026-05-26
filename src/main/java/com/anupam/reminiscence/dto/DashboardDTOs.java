package com.anupam.reminiscence.dto;

import com.anupam.reminiscence.constants.Level;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class DashboardDTOs {

    @Data
    @Builder
    public static class MetricsResponse {
        private int currentStreak;
        private long totalConceptsCount;
        private int averageMasteryScore;
        private long dueTomorrowCount;
        private long dueNextWeekCount;
    }

    @Data
    @Builder
    public static class ActivityDetailsResponse {
        private List<DailyLogDTO> dailyLogs;
        private List<RevisionLogDTO> revisionLogs;
    }

    @Data
    @Builder
    public static class DailyLogDTO {
        private UUID id;
        private String rawInput;
        private String status;
        private List<String> extractedTopics;
    }

    @Data
    @Builder
    public static class RevisionLogDTO {
        private String conceptName;
        private Level quality;
        private String reviewedAt;
        private Integer masteryScore;
        private Integer failureCount;
        private LocalDateTime createdAt;
        private LocalDate nextReviewDate;
        private Integer reviewCount;
    }
}