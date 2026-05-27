package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.dto.DashboardDTOs.*;
import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.repo.*;
import com.anupam.reminiscence.service.DashboardService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserConceptRepo userConceptRepo;
    private final ReviewHistoryRepo reviewHistoryRepo;
    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final ConceptRepo conceptRepo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public MetricsResponse getDashboardMetrics(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextWeekEnd = today.plusDays(7);

        return MetricsResponse.builder()
                .currentStreak(calculateActiveStreak(userId))
                .totalConceptsCount(userConceptRepo.countByUserId(userId))
                .averageMasteryScore(userConceptRepo.findAverageMasteryScoreByUserId(userId).intValue())
                .dueTomorrowCount(userConceptRepo.countConceptsDueOnDate(userId, tomorrow))
                .dueNextWeekCount(userConceptRepo.countConceptsDueBetweenDates(userId, today, nextWeekEnd))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getMatrixHeatmap(UUID userId) {
        LocalDateTime T365DaysAgo = LocalDate.now().minusDays(364).atStartOfDay();

        List<LocalDateTime> structuralReviews = reviewHistoryRepo.findAllReviewTimestampsSince(userId, T365DaysAgo);
        List<LocalDateTime> rawEntries = dailyEntryItemRepo.findAllEntryTimestampsSince(userId, T365DaysAgo);

        Map<String, Integer> complexMap = new HashMap<>();
        DateTimeFormatter spatialFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (LocalDateTime dt : structuralReviews) {
            String trackingDate = dt.format(spatialFormatter);
            complexMap.put(trackingDate, complexMap.getOrDefault(trackingDate, 0) + 1);
        }

        for (LocalDateTime dt : rawEntries) {
            String trackingDate = dt.format(spatialFormatter);
            complexMap.put(trackingDate, complexMap.getOrDefault(trackingDate, 0) + 1);
        }

        return complexMap;
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDetailsResponse getDailyActivityDetails(UUID userId, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

        // Fetch inputs saved on this matrix node coordinate
        List<DailyLogDTO> logs = dailyEntryItemRepo.findEntriesByDateRange(userId, dayStart, dayEnd)
                .stream().map(entry -> {
                    List<String> topics = new ArrayList<>();
                    try {
                        if (entry.getTopicExtracted() != null && !entry.getTopicExtracted().isBlank()) {
                            topics = objectMapper.readValue(entry.getTopicExtracted(), new TypeReference<List<String>>() {});
                        }
                    } catch (Exception e) {
                        // Soft structural fallback
                    }
                    return DailyLogDTO.builder()
                            .id(entry.getId())
                            .rawInput(entry.getRawTopic())
                            .status(entry.getProcessingStatus())
                            .extractedTopics(topics)
                            .build();
                }).collect(Collectors.toList());

        // Fetch verification logs executed on this matrix coordinate
        List<RevisionLogDTO> revisions = reviewHistoryRepo.findHistoricalReviewsByDate(userId, dayStart, dayEnd)
                .stream().map(history -> {
                    ConceptEntity target = conceptRepo.findById(history.getConceptId()).orElse(null);
                    UserConceptEntity userConcept = userConceptRepo.findByUserIdAndConceptId(history.getUserId(),history.getConceptId()).orElse(null);
                    return RevisionLogDTO.builder()
                            .conceptName(target != null ? target.getName() : "Unknown Core Reference")
                            .quality(history.getQuality())
                            .reviewedAt(history.getReviewedAt().toString())
                            .masteryScore(userConcept.getMasteryScore())
                            .nextReviewDate(userConcept.getNextReviewDate())
                            .createdAt(userConcept.getCreatedAt())
                            .failureCount(userConcept.getFailureCount())
                            .reviewCount(userConcept.getReviewCount())
                            .build();
                }).collect(Collectors.toList());

        return ActivityDetailsResponse.builder()
                .dailyLogs(logs)
                .revisionLogs(revisions)
                .build();
    }

    private int calculateActiveStreak(UUID userId) {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay();
        Set<LocalDate> activeDates = new HashSet<>();

        reviewHistoryRepo.findAllReviewTimestampsSince(userId, thirtyDaysAgo)
                .forEach(dt -> activeDates.add(dt.toLocalDate()));
        dailyEntryItemRepo.findAllEntryTimestampsSince(userId, thirtyDaysAgo)
                .forEach(dt -> activeDates.add(dt.toLocalDate()));

        int continuousStreak = 0;
        LocalDate indexTracker = LocalDate.now();

        // Check backwards to see how long the active streak has been active
        while (activeDates.contains(indexTracker)) {
            continuousStreak++;
            indexTracker = indexTracker.minusDays(1);
        }

        // Check if user has missed today but maintained yesterday's momentum pass
        if (continuousStreak == 0) {
            indexTracker = LocalDate.now().minusDays(1);
            while (activeDates.contains(indexTracker)) {
                continuousStreak++;
                indexTracker = indexTracker.minusDays(1);
            }
        }

        return continuousStreak;
    }
}