package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.ai_provider.AIOrchestratorService;
import com.anupam.reminiscence.constants.Level;
import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.dto.ConceptReviewResponse;
import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.entity.ReviewHistoryEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.repo.ConceptRepo;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.repo.ReviewHistoryRepo;
import com.anupam.reminiscence.repo.UserConceptRepo;
import com.anupam.reminiscence.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.anupam.reminiscence.constants.Level.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final ConceptProcessingService conceptProcessingService;
    private final AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;
    private final UserConceptRepo userConceptRepo;
    private final ConceptRepo conceptRepo;

    @Override
    public void saveDailyEntry(String text, UUID userId) {

        try {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Entry text cannot be empty");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);

            // Check if an entry already exists for today
            DailyEntryItemEntity entry = dailyEntryItemRepo
                    .findTodayEntryByUserId(userId, startOfDay, endOfDay)
                    .map(existing -> {
                        // Append new text to today's entry, reset to PENDING
                        existing.setRawTopic(text.trim());
                        existing.setProcessingStatus(ProcessStatus.PENDING.name());
                        existing.setUpdatedAt(now);
                        return existing;
                    })
                    .orElse(DailyEntryItemEntity.builder()
                            .userId(userId)
                            .rawTopic(text.trim())
                            .processingStatus(ProcessStatus.PENDING.name())
                            .createdAt(now)
                            .updatedAt(now)
                            .build()
                    );

            List<String> topicExtracted = aiOrchestratorService
                    .extractTopics(text)
                    .getTopics();

            String json = objectMapper.writeValueAsString(topicExtracted);

            entry.setTopicExtracted(json);

            DailyEntryItemEntity saved = dailyEntryItemRepo.save(entry);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConceptReviewResponse> findAllPending(UUID userId) {
        LocalDate today = LocalDate.now();
        List<UserConceptEntity> pendingUserConcepts = userConceptRepo.findPendingReviews(userId, today);

        return pendingUserConcepts.stream().map(uc -> {
            ConceptEntity concept = conceptRepo.findById(uc.getConceptId())
                    .orElseThrow(() -> new IllegalStateException("Concept content missing"));

            return ConceptReviewResponse.builder()
                    .userConceptId(uc.getId())
                    .conceptId(concept.getId())
                    .name(concept.getName())
                    .questionText(concept.getQuestionText())
                    .answerText(concept.getAnswerText())
                    .keyNotes(concept.getKeyNotes())
                    .difficulty(concept.getDifficulty())
                    .masteryScore(uc.getMasteryScore())
                    .currentIntervalDays(uc.getCurrentIntervalDays())
                    .nextReviewDate(uc.getNextReviewDate())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public Integer findAllPendingCount(UUID userId) {
        LocalDate today = LocalDate.now();
        return (Integer) userConceptRepo.findPendingReviewsCount(userId, today);
    }

    // Inject this into your existing UserServiceImpl
    private final ReviewHistoryRepo reviewHistoryRepo;

    @Override
    @Transactional
    public void reviewConcept(UUID userId, UUID userConceptId, Level rating) {
        UserConceptEntity userConcept = userConceptRepo
                .findByIdAndUserId(userConceptId, userId)
                .orElseThrow(() -> new RuntimeException("Review card not found"));

        int interval = userConcept.getCurrentIntervalDays();
        int mastery = userConcept.getMasteryScore();
        int failures = userConcept.getFailureCount();
        int reviewCount = userConcept.getReviewCount();

        double easeFactor = calculateEaseFactor(mastery, failures);

        switch (rating) {
            case EASY -> {
                interval = reviewCount == 0 ? 3 : Math.max(interval + 1, (int) Math.round(interval * easeFactor * 1.3));
                mastery = Math.min(100, mastery + 12);
            }
            case MEDIUM -> {
                interval = reviewCount == 0 ? 1 : Math.max(interval + 1, (int) Math.round(interval * easeFactor));
                mastery = Math.min(100, mastery + 5);
            }
            case HARD -> {
                interval = 1;
                mastery = Math.max(0, mastery - 8);
                failures++;
            }
        }

        userConcept.setCurrentIntervalDays(interval);
        userConcept.setMasteryScore(mastery);
        userConcept.setFailureCount(failures);
        userConcept.setReviewCount(reviewCount + 1);
        userConcept.setLastReviewedAt(LocalDateTime.now());
        userConcept.setNextReviewDate(LocalDate.now().plusDays(interval));
        userConcept.setUpdatedAt(LocalDateTime.now());

        userConceptRepo.save(userConcept);

        // ================== CRUCIAL PERSISTENCE INSERTION FOR HEATMAP ENGINE ==================
        ReviewHistoryEntity log = ReviewHistoryEntity.builder()
                .userId(userId)
                .conceptId(userConcept.getConceptId())
                .quality(rating)
                .reviewedAt(LocalDateTime.now())
                .build();
        reviewHistoryRepo.save(log);
    }
    private double calculateEaseFactor(int mastery, int failures) {
        double ease = 1.8;

        ease += (mastery / 100.0) * 0.8;

        ease -= failures * 0.15;

        return Math.max(1.3, Math.min(2.8, ease));
    }
}