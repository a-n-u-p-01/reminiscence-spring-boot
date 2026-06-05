package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.ai_provider.AIOrchestratorService;
import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.dto.ConceptReviewResponse;
import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.repo.ConceptRepo;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.repo.ReviewHistoryRepo;
import com.anupam.reminiscence.repo.UserConceptRepo;
import com.anupam.reminiscence.entity.UserEntity;

import java.time.Instant;
import java.time.ZoneId;
import com.anupam.reminiscence.repo.UserRepository;
import com.anupam.reminiscence.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final ConceptRepo conceptRepo;
    private final AIOrchestratorService aiOrchestratorService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final UserConceptRepo userConceptRepo;
    private final ReviewHistoryRepo reviewHistoryRepo;

    @Override
    public void saveDailyEntryTopic(List<String> topics, UUID userId) {

        try {
            if (topics == null || topics.isEmpty()) {
                throw new IllegalArgumentException("Entry topics cannot be empty");
            }

            String topicJson = objectMapper.writeValueAsString(topics);

            // Fetch user's timezone
            ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
            Instant now = Instant.now();
            LocalDate today = LocalDate.ofInstant(now, zoneId);
            Instant startOfDayDt = today.atStartOfDay(zoneId).toInstant();
            Instant endOfDayDt = today.plusDays(1).atStartOfDay(zoneId).toInstant();

            // Check if an entry already exists for today
            DailyEntryItemEntity entry = dailyEntryItemRepo
                    .findTodayEntryByUserId(userId, startOfDayDt, endOfDayDt)
                    .map(existing -> {
                        // Append new text to today's entry, reset to PENDING
                        existing.setTopicExtracted(topicJson);
                        existing.setProcessingStatus(ProcessStatus.PENDING.name());
                        existing.setUpdatedAt(now);
                        return existing;
                    })
                    .orElse(DailyEntryItemEntity.builder()
                            .userId(userId)
                            .topicExtracted(topicJson)
                            .processingStatus(ProcessStatus.PENDING.name())
                            .createdAt(now)
                            .updatedAt(now)
                            .build()
                    );

            DailyEntryItemEntity saved = dailyEntryItemRepo.save(entry);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    @Override
    public void saveDailyEntry(String text, UUID userId) {

        try {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Entry text cannot be empty");
            }

            // Fetch user's timezone
            ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
            Instant now = Instant.now();
            LocalDate today = LocalDate.ofInstant(now, zoneId);
            Instant startOfDayDt = today.atStartOfDay(zoneId).toInstant();
            Instant endOfDayDt = today.plusDays(1).atStartOfDay(zoneId).toInstant();

            // Check if an entry already exists for today
            DailyEntryItemEntity entry = dailyEntryItemRepo
                    .findTodayEntryByUserId(userId, startOfDayDt, endOfDayDt)
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
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConceptReviewResponse> findAllPending(UUID userId) {
        ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
        LocalDate today = LocalDate.now(zoneId);
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
        ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
        LocalDate today = LocalDate.now(zoneId);
        return (Integer) userConceptRepo.findPendingReviewsCount(userId, today);
    }

    @Override
    @Transactional
    public void updateTopics(List<String> topics, UUID userId) {
        try {
            if (topics == null) {
                throw new IllegalArgumentException("Topics configuration cannot be null");
            }

            // 1. Resolve localized chronologies to lock onto today
            ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
            Instant now = Instant.now();
            LocalDate today = LocalDate.ofInstant(now, zoneId);
            Instant startOfDayDt = today.atStartOfDay(zoneId).toInstant();
            Instant endOfDayDt = today.plusDays(1).atStartOfDay(zoneId).toInstant();

            // 2. Locate today's entry. If not present, do nothing.
            java.util.Optional<DailyEntryItemEntity> existingEntryOpt =
                    dailyEntryItemRepo.findTodayEntryByUserId(userId, startOfDayDt, endOfDayDt);

            if (existingEntryOpt.isEmpty()) {
                // STOPS EXECUTION: If no daily entry exists for today, do nothing.
                return;
            }

            DailyEntryItemEntity entry = existingEntryOpt.get();

            // 3. Update existing properties and serialize topics
            entry.setProcessingStatus(ProcessStatus.PENDING.name());
            entry.setUpdatedAt(now);

            String jsonPayload = objectMapper.writeValueAsString(topics);
            entry.setTopicExtracted(jsonPayload);

            // 4. Save updates back to persistence layers
            dailyEntryItemRepo.save(entry);

        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new RuntimeException("Failed to update extracted topics configuration matrix", e);
        }
    }

}