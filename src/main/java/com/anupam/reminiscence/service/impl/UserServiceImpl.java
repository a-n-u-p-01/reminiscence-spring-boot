package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.ai_provider.AIOrchestratorService;
import com.anupam.reminiscence.ai_provider.AIProvider;
import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.dto.ConceptItemDTO;
import com.anupam.reminiscence.dto.ConceptReviewResponse;
import com.anupam.reminiscence.dto.UserConceptRequest;
import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.repo.ConceptRepo;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.repo.ReviewHistoryRepo;
import com.anupam.reminiscence.repo.UserConceptRepo;
import com.anupam.reminiscence.repo.UserRepository;
import com.anupam.reminiscence.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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
    private final Map<String, AIProvider> providerMap;

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
            ConceptEntity concept = conceptRepo.findById(uc.getConcept().getId())
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

            // Resolve localized chronologies to lock onto today
            ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
            Instant now = Instant.now();
            LocalDate today = LocalDate.ofInstant(now, zoneId);
            Instant startOfDayDt = today.atStartOfDay(zoneId).toInstant();
            Instant endOfDayDt = today.plusDays(1).atStartOfDay(zoneId).toInstant();

            // Locate today's entry. If not present, do nothing.
            java.util.Optional<DailyEntryItemEntity> existingEntryOpt =
                    dailyEntryItemRepo.findTodayEntryByUserId(userId, startOfDayDt, endOfDayDt);

            if (existingEntryOpt.isEmpty()) {
                return;
            }

            DailyEntryItemEntity entry = existingEntryOpt.get();

            // Update existing properties and serialize topics
            entry.setProcessingStatus(ProcessStatus.PENDING.name());
            entry.setUpdatedAt(now);

            String jsonPayload = objectMapper.writeValueAsString(topics);
            entry.setTopicExtracted(jsonPayload);

            dailyEntryItemRepo.save(entry);

        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new RuntimeException("Failed to update extracted topics configuration matrix", e);
        }
    }

    @Override
    @Transactional
    public void saveOrUpdateUserConcept(UserConceptRequest request, UUID userId) {
        try {
            if (request == null || request.getConceptName() == null || request.getConceptName().isBlank()) {
                throw new IllegalArgumentException("Concept name cannot be empty");
            }

            Instant now = Instant.now();
            ConceptEntity conceptEntity;

            // DETERMINE UPSERT PATH FOR GLOBAL CONCEPT MATRIX
            if (request.getConceptId() != null) {
                // UPDATE PATH
                conceptEntity = conceptRepo.findById(request.getConceptId())
                        .map(existing -> {
                            existing.setQuestionText(request.getQuestion() != null ? request.getQuestion() : existing.getQuestionText());
                            existing.setAnswerText(request.getMainNote() != null ? request.getMainNote() : existing.getAnswerText());
                            existing.setKeyNotes(request.getExtraNote() != null ? request.getExtraNote() : existing.getKeyNotes());
                            existing.setUpdatedAt(now);
                            return existing;
                        })
                        .orElseThrow(() -> new IllegalArgumentException("Concept node not found with given ID: " + request.getConceptId()));
            } else {
                // INSERT PATH
                conceptEntity = ConceptEntity.builder()
                        .name(request.getConceptName())
                        .normalizedName(request.getConceptName().trim().toLowerCase())
                        .questionText(request.getQuestion() != null ? request.getQuestion() : request.getConceptName())
                        .answerText(request.getMainNote() != null ? request.getMainNote() : "")
                        .keyNotes(request.getExtraNote() != null ? request.getExtraNote() : "")
                        .difficulty("NA")
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
            }

            // Persist parent concept mapping details
            ConceptEntity savedConcept = conceptRepo.save(conceptEntity);

            // RESOLVE USER-SPECIFIC RETENTION LAYER
            ZoneId zoneId = ZoneId.of(userRepository.findById(userId).map(UserEntity::getTimezone).orElse("UTC"));
            LocalDate today = LocalDate.now(zoneId);

            userConceptRepo.findByUserIdAndConceptId(userId, savedConcept.getId())
                    .map(existingUserConcept -> {
                        existingUserConcept.setUpdatedAt(now);
                        return userConceptRepo.save(existingUserConcept);
                    })
                    .orElseGet(() -> {
                        UserConceptEntity newUserConcept = UserConceptEntity.builder()
                                .userId(userId)
                                .concept(savedConcept)
                                .masteryScore(0)
                                .currentIntervalDays(1)
                                .nextReviewDate(today)
                                .reviewCount(0)
                                .failureCount(0)
                                .createdAt(now)
                                .updatedAt(now)
                                .stability(1.0)
                                .difficulty(5.0)
                                .build();
                        return userConceptRepo.save(newUserConcept);
                    });
        } catch (Exception e) {
            log.error("Error occurred: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public void deleteConcept(UUID conceptId, UUID id) {
        try {
            List<UserConceptEntity> userConceptEntities = userConceptRepo.findAllByConceptId(conceptId);
            if (!userConceptEntities.isEmpty()) {
                userConceptRepo.deleteAll(userConceptEntities);
            }
            reviewHistoryRepo.deleteByConceptId(conceptId);
            conceptRepo.deleteById(conceptId);
        } catch (Exception e) {
            log.error("Error occurred: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public ConceptItemDTO regenerateConcept(String conceptId, String modelName) {
        try {
            if (conceptId == null || conceptId.isBlank()) {
                throw new IllegalArgumentException("Concept ID cannot be null or empty");
            }
            if (modelName == null || modelName.isBlank()) {
                throw new IllegalArgumentException("Model provider name cannot be null or empty");
            }

            UUID id = UUID.fromString(conceptId);

            // 1. Fetch the target core entity
            ConceptEntity concept = conceptRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Concept node not found with ID: " + conceptId));

            log.info("Regenerating flashcard concept: '{}' using specific provider bean: {}", concept.getName(), modelName);

            // 2. Fetch the specific bean from our map registry based on the sanitized upper-case name
            String registryKey = modelName.trim().toUpperCase();
            AIProvider aiProvider = providerMap.get(registryKey);

            if (aiProvider == null) {
                log.warn("AIProvider bean '{}' not found. Defaulting to fallback engine: SILICONFLOW", registryKey);
                aiProvider = providerMap.get("SILICONFLOW");
                if (aiProvider == null) {
                    throw new IllegalStateException("Critical configuration failure: No AIProvider beans found in context map registry.");
                }
            }

            // 3. Make the remote layout generation call
            FlashcardResponse aiResponse = aiProvider.generateFlashcards(List.of(concept.getName()));
            if (aiResponse == null || aiResponse.getFlashcardList() == null || aiResponse.getFlashcardList().isEmpty()) {
                throw new RuntimeException("Target AI Provider (" + aiProvider.getProviderName() + ") returned an empty dataset variant.");
            }

            var freshCard = aiResponse.getFlashcardList().get(0);

            // 4. Overwrite text configurations and commit persistence updates
            Instant now = Instant.now();
            concept.setQuestionText(freshCard.getQuestion());
            concept.setAnswerText(freshCard.getAnswer());
            concept.setKeyNotes(freshCard.getNotes());
            concept.setUpdatedAt(now);

//            ConceptEntity updatedConcept = conceptRepo.save(concept);

            // 5. Unpack context parameters cleanly into the client payload contract
            return ConceptItemDTO.builder()
                    .question(freshCard.getQuestion())
                    .mainNote(freshCard.getAnswer())
                    .extraNote(freshCard.getNotes())
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid structural identifier string configuration passed down: {}", conceptId);
            throw e;
        } catch (Exception e) {
            log.error("Critical failure during backend flashcard content overwriting context flow for ID: {}", conceptId, e);
            throw new RuntimeException("Flashcard generation background operation variant failed", e);
        }
    }

    @Override
    @Transactional
    public void savePushToken(String token, UUID userId) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Device push token mapping configuration cannot be empty");
        }

        userRepository.findById(userId).ifPresent(user -> {
            // Trims white space variations and saves the live key token down to the column entity
            user.setPushToken(token.trim());
            userRepository.save(user);
            log.info("Successfully updated live device FCM token mapping for User ID: {}", userId);
        });
    }
}