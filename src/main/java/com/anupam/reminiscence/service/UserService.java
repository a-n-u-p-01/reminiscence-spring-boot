package com.anupam.reminiscence.service;

import com.anupam.reminiscence.dto.ConceptReviewResponse;
import com.anupam.reminiscence.dto.UserConceptRequest;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;
import java.util.UUID;

public interface UserService {
    void saveDailyEntry(String text, UUID userId) throws JsonProcessingException;
    List<ConceptReviewResponse> findAllPending(UUID userId);

    Integer findAllPendingCount(UUID id);

//    void reviewConcept(UUID userId, UUID userConceptId, Level rating);

    void updateTopics(List<String> topics, UUID id);

    void saveDailyEntryTopic(List<String> topics, UUID id);
    void saveOrUpdateUserConcept(UserConceptRequest request, UUID userId);

    void deleteConcept(UUID conceptId, UUID id);
}