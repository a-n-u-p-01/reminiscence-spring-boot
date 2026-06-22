package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.ai_provider.AIOrchestratorService;
import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.dto.ai.Flashcard;
import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.repo.ConceptRepo;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.repo.UserConceptRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import com.anupam.reminiscence.repo.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConceptProcessingService {

    private final AIOrchestratorService aiOrchestratorService;
    private final ConceptRepo conceptRepository;
    private final UserConceptRepo userConceptRepo;
    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    private static final int TYPO_DISTANCE_THRESHOLD = 2;
    private final LevenshteinDistance levenshtein = new LevenshteinDistance();
    private final EntryStatusService entryStatusService;


    @Transactional
    public void processEntry(DailyEntryItemEntity entry) {
        try {

            // ── User entered topic list─────────────────────
            List<String> rawTopics = objectMapper.readValue(
                    entry.getTopicExtracted(),
                    new TypeReference<List<String>>() {}
            );

            if (rawTopics == null || rawTopics.isEmpty()) {
                markEntry(entry, ProcessStatus.SUCCESS, "Topic list is empty. Time: " + Instant.now());
                return;
            }

            List<String> normalizedTopics = rawTopics.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(t -> t.trim().toLowerCase().replaceAll("\\s+", " "))
                    .distinct()
                    .toList();

            // ── Step 1: Exact DB filter ──────────────────────────────────────
            Set<String> exactMatches = new HashSet<>(
                    conceptRepository.findExistingNormalizedNamesUserId(normalizedTopics,entry.getUserId())
            );


            //remove those exact matches from normalizedTopics which proceed for next
            List<String> normalizedAfterExact = new ArrayList<>();

            for (String s: normalizedTopics) {
                if (!exactMatches.contains(s)) {
                    normalizedAfterExact.add(s);
                }
            }

            if (normalizedAfterExact.isEmpty()) {
                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
                return;
            }

            // ── Step 2: Fuzzy candidates ─────────────────────────────────────
            List<String> candidates = new ArrayList<>();
            for (String norm : normalizedAfterExact) {
                candidates.addAll(conceptRepository.findFuzzyMatchesAndUserId(norm,entry.getUserId()));
            }
            candidates = candidates.stream().distinct().toList();


            // ── AI Call 1: Semantic dedup ────────────────────────────────────
            List<String> confirmedNewTopics = aiOrchestratorService
                    .detectNewTopics(normalizedAfterExact, candidates)
                    .getNewTopics();

            if (confirmedNewTopics == null || confirmedNewTopics.isEmpty()) {
                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
                return;
            }

            // ── AI Call 2: Chunked Flashcard generation ─────────────────────────────
            List<Flashcard> allGeneratedFlashcards = new ArrayList<>();
            int chunkSize = 4;

            for (String topic: confirmedNewTopics) {

                try {
                    String type = aiOrchestratorService.classifyTopic(topic);
                    Flashcard response = aiOrchestratorService.generateFlashcardWithType(topic,type);
                    if (response != null) {
                        allGeneratedFlashcards.add(response);
                    }
                } catch (Exception e) {
                    log.error("Failed to generate fl for {}: {}", topic, e.getMessage());
                }
            }

            if (allGeneratedFlashcards.isEmpty()) {
                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
                return;
            }



            List<ConceptEntity> generatedConcepts = allGeneratedFlashcards.stream()
                    .map(flashcard -> ConceptEntity.builder()
                            .name(flashcard.getConceptName())
                            .normalizedName(
                                    flashcard.getConceptName()
                                            .trim()
                                            .toLowerCase()
                                            .replaceAll("\\s+", " ")
                            )
                            .questionText(flashcard.getQuestion())
                            .answerText(flashcard.getAnswer())
                            .keyNotes(flashcard.getNotes())
                            .difficulty("MEDIUM")
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build())
                    .toList();

            // Save new concepts and create UserConceptEntity for each
            if (!generatedConcepts.isEmpty()) {
                List<ConceptEntity> savedConcepts = conceptRepository.saveAll(generatedConcepts);
                createUserConceptsIfMissing(savedConcepts, entry.getUserId(), entry);
            }


            markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");

        } catch (Exception e) {
            log.error("Failed to process entry {}: {}", entry.getUserId(), e.getMessage(), e);

            entryStatusService.markAsFailed(entry, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    private void createUserConceptsIfMissing(List<ConceptEntity> concepts, UUID userId, DailyEntryItemEntity dailyEntryItemEntity) {
        Instant now = Instant.now();

        // 1. Fetch user's registered timezone to correctly calculate local contextual day boundaries
        String userTimezone = userRepository.findById(userId)
                .map(UserEntity::getTimezone)
                .orElse("UTC");
        ZoneId userZone = ZoneId.of(userTimezone);

        // 2. Map absolute Instants to target user's local date utilizing the explicit ZoneId context
        LocalDate baseLocalDate = dailyEntryItemEntity.getCreatedAt() != null
                ? LocalDate.ofInstant(dailyEntryItemEntity.getCreatedAt(), userZone)
                : LocalDate.ofInstant(now, userZone);

        List<UserConceptEntity> toCreate = concepts.stream()
                .filter(concept ->
                        !userConceptRepo.existsByUserIdAndConceptId(userId, concept.getId())
                )
                .map(concept -> UserConceptEntity.builder()
                        .userId(userId)
                        .concept(concept)
                        .masteryScore(0)
                        .currentIntervalDays(1)
                        .nextReviewDate(baseLocalDate.plusDays(1))
                        .lastReviewedAt(null)
                        .stability(1.0)
                        .difficulty(5.0)
                        .reviewCount(0)
                        .failureCount(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();

        if (!toCreate.isEmpty()) {
            userConceptRepo.saveAll(toCreate);
        }
    }


    @Transactional(propagation = Propagation.NEVER)
    public void markEntry(DailyEntryItemEntity entry,
                          ProcessStatus status,
                          String comment) {

        entry.setProcessingStatus(status.name());
        entry.setProcessComment(comment);
        entry.setUpdatedAt(Instant.now());

        dailyEntryItemRepo.save(entry);
    }

//    @Transactional
//    public void processEntry(DailyEntryItemEntity entry) {
//        try {
//
//            // ── AI Call 0: Extract topics from free text ─────────────────────
//            List<String> rawTopics = objectMapper.readValue(
//                    entry.getTopicExtracted(),
//                    new TypeReference<List<String>>() {}
//            );
//
//            if (rawTopics == null || rawTopics.isEmpty()) {
//                markEntry(entry, ProcessStatus.SUCCESS, "Extracted topic list is empty. Time: " + Instant.now());
//                return;
//            }
//
//            List<String> normalizedTopics = rawTopics.stream()
//                    .filter(t -> t != null && !t.isBlank())
//                    .map(t -> t.trim().toLowerCase().replaceAll("\\s+", " "))
//                    .distinct()
//                    .toList();
//
//            // ── Step 1: Exact DB filter ──────────────────────────────────────
//            Set<String> exactMatches = new HashSet<>(
//                    conceptRepository.findExistingNormalizedNames(normalizedTopics)
//            );
//
//            // Concepts already in DB — create UserConceptEntity if missing for this user
//            // e.g. concept was created by another user or before this user registered
//            if (!exactMatches.isEmpty()) {
//                List<ConceptEntity> alreadyExistingConcepts =
//                        conceptRepository.findByNormalizedNameIn(new ArrayList<>(exactMatches));
//                createUserConceptsIfMissing(alreadyExistingConcepts, entry.getUserId(), entry);
//            }
//
//            List<String> afterExactFilter = new ArrayList<>();
//            List<String> normalizedAfterExact = new ArrayList<>();
//
//            for (int i = 0; i < rawTopics.size(); i++) {
//                String norm = normalizedTopics.get(i);
//                if (!exactMatches.contains(norm)) {
//                    afterExactFilter.add(rawTopics.get(i));
//                    normalizedAfterExact.add(norm);
//                }
//            }
//
//            if (afterExactFilter.isEmpty()) {
//                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
//                return;
//            }
//
//            // ── Step 2: Fuzzy candidates ─────────────────────────────────────
//            List<String> candidates = new ArrayList<>();
//            for (String norm : normalizedAfterExact) {
//                candidates.addAll(conceptRepository.findFuzzyMatches(norm));
//            }
//            candidates = candidates.stream().distinct().toList();
//
//            // ── Step 3: Levenshtein typo filter ─────────────────────────────
//            List<String> afterTypoFilter = new ArrayList<>();
//
//            for (int i = 0; i < afterExactFilter.size(); i++) {
//                String norm = normalizedAfterExact.get(i);
//                boolean isTypo = candidates.stream().anyMatch(candidate -> {
//                    Integer distance = levenshtein.apply(norm, candidate);
//                    return distance != -1 && distance <= TYPO_DISTANCE_THRESHOLD;
//                });
//                if (!isTypo) {
//                    afterTypoFilter.add(afterExactFilter.get(i));
//                } else {
//                    // Typo of existing concept — still wire up UserConceptEntity
//                    candidates.stream()
//                            .filter(c -> {
//                                Integer d = levenshtein.apply(norm, c);
//                                return d != -1 && d <= TYPO_DISTANCE_THRESHOLD;
//                            })
//                            .findFirst()
//                            .ifPresent(matchedCandidate -> {
//                                conceptRepository.findByNormalizedName(matchedCandidate)
//                                        .ifPresent(concept ->
//                                                createUserConceptsIfMissing(List.of(concept), entry.getUserId(), entry)
//                                        );
//                            });
//                }
//            }
//
//            if (afterTypoFilter.isEmpty()) {
//                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
//                return;
//            }
//
//            // ── AI Call 1: Semantic dedup ────────────────────────────────────
//            List<String> confirmedNewTopics = aiOrchestratorService
//                    .detectNewTopics(afterTypoFilter, candidates)
//                    .getNewTopics();
//
//            if (confirmedNewTopics == null || confirmedNewTopics.isEmpty()) {
//                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
//                return;
//            }
//
//            // ── AI Call 2: Flashcard generation ─────────────────────────────
//            FlashcardResponse response =
//                    aiOrchestratorService.generateFlashcards(confirmedNewTopics);
//
//            if (response.getFlashcardList() == null || response.getFlashcardList().isEmpty()) {
//                markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
//                return;
//            }
//
//            Instant now = Instant.now();
//
//            List<ConceptEntity> generatedConcepts = response.getFlashcardList().stream()
//                    .map(flashcard -> ConceptEntity.builder()
//                            .name(flashcard.getConceptName())
//                            .normalizedName(
//                                    flashcard.getConceptName()
//                                            .trim()
//                                            .toLowerCase()
//                                            .replaceAll("\\s+", " ")
//                            )
//                            .questionText(flashcard.getQuestion())
//                            .answerText(flashcard.getAnswer())
//                            .keyNotes(flashcard.getNotes())
//                            .difficulty("MEDIUM")
//                            .createdAt(now)
//                            .updatedAt(now)
//                            .build())
//                    .toList();
//
//            // Final safety check
//            List<String> generatedNorm = generatedConcepts.stream()
//                    .map(ConceptEntity::getNormalizedName).toList();
//
//            Set<String> alreadyExists = new HashSet<>(
//                    conceptRepository.findExistingNormalizedNames(generatedNorm)
//            );
//
//            List<ConceptEntity> toSave = generatedConcepts.stream()
//                    .filter(c -> !alreadyExists.contains(c.getNormalizedName()))
//                    .toList();
//
//            // Save new concepts and create UserConceptEntity for each
//            if (!toSave.isEmpty()) {
//                List<ConceptEntity> savedConcepts = conceptRepository.saveAll(toSave);
//                createUserConceptsIfMissing(savedConcepts, entry.getUserId(), entry);
//            }
//
//            // Concepts that existed at final check — still wire up UserConceptEntity
//            if (!alreadyExists.isEmpty()) {
//                List<ConceptEntity> alreadyExisting =
//                        conceptRepository.findByNormalizedNameIn(new ArrayList<>(alreadyExists));
//                createUserConceptsIfMissing(alreadyExisting, entry.getUserId(), entry);
//            }
//
//            markEntry(entry, ProcessStatus.SUCCESS, "Successfully Processed");
//
//        } catch (Exception e) {
//            log.error("Failed to process entry {}: {}", entry.getUserId(), e.getMessage(), e);
//
//            entryStatusService.markAsFailed(entry, e.getClass().getSimpleName() + ": " + e.getMessage());
//        }
//    }

    // Creates UserConceptEntity only if one doesn't already exist for this user+concept
//    private void createUserConceptsIfMissing(List<ConceptEntity> concepts, UUID userId, DailyEntryItemEntity dailyEntryItemEntity) {
//        Instant now = Instant.now();
//
//        // 1. Fetch user's registered timezone to correctly calculate local contextual day boundaries
//        String userTimezone = userRepository.findById(userId)
//                .map(u -> u.getTimezone())
//                .orElse("UTC");
//        ZoneId userZone = ZoneId.of(userTimezone);
//
//        // 2. Map absolute Instants to target user's local date utilizing the explicit ZoneId context
//        LocalDate baseLocalDate = dailyEntryItemEntity.getCreatedAt() != null
//                ? LocalDate.ofInstant(dailyEntryItemEntity.getCreatedAt(), userZone)
//                : LocalDate.ofInstant(now, userZone);
//
//        List<UserConceptEntity> toCreate = concepts.stream()
//                .filter(concept ->
//                        !userConceptRepo.existsByUserIdAndConceptId(userId, concept.getId())
//                )
//                .map(concept -> UserConceptEntity.builder()
//                        .userId(userId)
//                        .conceptId(concept.getId())
//                        .masteryScore(0)
//                        .currentIntervalDays(1)
//                        .nextReviewDate(baseLocalDate.plusDays(1))
//                        .lastReviewedAt(null)
//                        .stability(1.0)
//                        .difficulty(5.0)
//                        .reviewCount(0)
//                        .failureCount(0)
//                        .createdAt(now)
//                        .updatedAt(now)
//                        .build())
//                .toList();
//
//        if (!toCreate.isEmpty()) {
//            userConceptRepo.saveAll(toCreate);
//        }
//    }
//
//
//    @Transactional(propagation = Propagation.NEVER)
//    public void markEntry(DailyEntryItemEntity entry,
//                          ProcessStatus status,
//                          String comment) {
//
//        entry.setProcessingStatus(status.name());
//        entry.setProcessComment(comment);
//        entry.setUpdatedAt(Instant.now());
//
//        dailyEntryItemRepo.save(entry);
//    }
}