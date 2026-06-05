package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.dto.ConceptExplorerResponse;
import com.anupam.reminiscence.dto.ConceptItemDTO;
import com.anupam.reminiscence.dto.DashboardDTOs.*;
import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.repo.*;
import com.anupam.reminiscence.service.DashboardService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import com.anupam.reminiscence.repo.UserRepository;
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
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public MetricsResponse getDashboardMetrics(UUID userId) {
        ZoneId zoneId = ZoneId.of(userRepository.findById(userId).orElseThrow().getTimezone());
        LocalDate today = LocalDate.now(zoneId);
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
        ZoneId zoneId = ZoneId.of(userRepository.findById(userId).orElseThrow().getTimezone());

        // Calculate the absolute Instant corresponding to the start of the day 364 days ago in user's zone
        Instant T365DaysAgo = LocalDate.now(zoneId).minusDays(364).atStartOfDay(zoneId).toInstant();

        List<Instant> structuralReviews = reviewHistoryRepo.findAllReviewTimestampsSince(userId, T365DaysAgo);
        List<Instant> rawEntries = dailyEntryItemRepo.findAllEntryTimestampsSince(userId, T365DaysAgo);

        Map<String, Integer> complexMap = new HashMap<>();
        DateTimeFormatter spatialFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId);

        for (Instant instant : structuralReviews) {
            String trackingDate = spatialFormatter.format(instant);
            complexMap.put(trackingDate, complexMap.getOrDefault(trackingDate, 0) + 1);
        }

        for (Instant instant : rawEntries) {
            String trackingDate = spatialFormatter.format(instant);
            complexMap.put(trackingDate, complexMap.getOrDefault(trackingDate, 0) + 1);
        }

        return complexMap;
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDetailsResponse getDailyActivityDetails(UUID userId, LocalDate date) {
        ZoneId zoneId = ZoneId.of(userRepository.findById(userId).orElseThrow().getTimezone());

        // Establish boundary intervals for the given calendar date inside the user's localized zone
        Instant dayStart = date.atStartOfDay(zoneId).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

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
                    UserConceptEntity userConcept = userConceptRepo.findByUserIdAndConceptId(history.getUserId(), history.getConceptId()).orElse(null);
                    return RevisionLogDTO.builder()
                            .conceptName(target != null ? target.getName() : "Unknown Core Reference")
                            .quality(history.getQuality())
                            .reviewedAt(history.getReviewedAt().toString())
                            .masteryScore(userConcept != null ? userConcept.getMasteryScore() : 0)
                            .nextReviewDate(userConcept != null ? userConcept.getNextReviewDate() : null)
                            .createdAt(userConcept != null ? userConcept.getCreatedAt() : null)
                            .failureCount(userConcept != null ? userConcept.getFailureCount() : 0)
                            .reviewCount(userConcept != null ? userConcept.getReviewCount() : 0)
                            .build();
                }).collect(Collectors.toList());

        return ActivityDetailsResponse.builder()
                .dailyLogs(logs)
                .revisionLogs(revisions)
                .build();
    }

    private int calculateActiveStreak(UUID userId) {
        ZoneId zoneId = ZoneId.of(userRepository.findById(userId).orElseThrow().getTimezone());
        Instant thirtyDaysAgo = LocalDate.now(zoneId).minusDays(30).atStartOfDay(zoneId).toInstant();
        Set<LocalDate> activeDates = new HashSet<>();

        reviewHistoryRepo.findAllReviewTimestampsSince(userId, thirtyDaysAgo)
                .forEach(instant -> activeDates.add(LocalDate.ofInstant(instant, zoneId)));

        dailyEntryItemRepo.findAllEntryTimestampsSince(userId, thirtyDaysAgo)
                .forEach(instant -> activeDates.add(LocalDate.ofInstant(instant, zoneId)));

        int continuousStreak = 0;
        LocalDate indexTracker = LocalDate.now(zoneId);

        // Check backwards to see how long the active streak has been active
        while (activeDates.contains(indexTracker)) {
            continuousStreak++;
            indexTracker = indexTracker.minusDays(1);
        }

        // Check if user has missed today but maintained yesterday's momentum pass
        if (continuousStreak == 0) {
            indexTracker = LocalDate.now(zoneId).minusDays(1);
            while (activeDates.contains(indexTracker)) {
                continuousStreak++;
                indexTracker = indexTracker.minusDays(1);
            }
        }

        return continuousStreak;
    }

    @Override
    @Transactional(readOnly = true)
    public ConceptExplorerResponse getConceptsExplorerList(UUID userId, String search, int page, boolean sortNewest) {
        // 1. Establish strict page configuration boundaries (Page limit hardcoded to 10)
        int fixedLimit = 15;

        // Determine sorting criteria based on the userConcept metadata timestamp
        Sort sortOrder = sortNewest
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(Sort.Direction.ASC, "createdAt");

        Pageable pageable = PageRequest.of(page, fixedLimit, sortOrder);

        // 2. Clear out loose spaces in the query string
        String sanitizedSearch = (search == null || search.trim().isEmpty()) ? null : search.trim();

        // 3. Fetch Paginated database data chunk
        Page<UserConceptEntity> databasePage = userConceptRepo.findConceptsByWorkspace(userId, sanitizedSearch, pageable);

        // 4. Map the elements into complete DTOs with inner structural concept notes
        List<ConceptItemDTO> mappedData = databasePage.getContent().stream()
                .map(userConcept -> {
                    // Fetch target descriptions mapping to the retention matrix index
                    ConceptEntity coreConcept = conceptRepo.findById(userConcept.getConceptId()).orElse(null);

                    return ConceptItemDTO.builder()
                            .conceptId(userConcept.getConceptId())
                            .conceptName(coreConcept != null ? coreConcept.getName() : "Unknown Core Reference")
                            .masteryScore(userConcept.getMasteryScore())
                            .reviewCount(userConcept.getReviewCount())
                            .failureCount(userConcept.getFailureCount())
                            .mainNote(coreConcept != null ? coreConcept.getAnswerText() : "")
                            .extraNote(coreConcept != null ? coreConcept.getKeyNotes() : "")
                            .createdAt(userConcept.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        // 5. Construct structural wrapping container matching the frontend requirements
        return ConceptExplorerResponse.builder()
                .data(mappedData)
                .currentPage(databasePage.getNumber() + 1) // Transform back to 1-indexed for frontend UI
                .totalPages(databasePage.getTotalPages())
                .totalItems(databasePage.getTotalElements())
                .build();
    }
}