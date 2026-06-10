package com.anupam.reminiscence.ai_provider;

import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.dto.ai.NewTopicsResponse;
import com.anupam.reminiscence.dto.ai.TopicsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIOrchestratorService {

    private final List<AIProvider> providers;

    public TopicsResponse extractTopics(String text) {
        for (AIProvider provider : providers) {
            try {
                return provider.extractTopics(text);
            } catch (Exception ex) {
                log.error("Provider {} failed topic extraction: {}", provider.getProviderName(), ex.getMessage());
            }
        }
        throw new RuntimeException("All AI providers failed during topic extraction");
    }

    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        for (AIProvider provider : providers) {
            try {
                if (candidates == null || candidates.isEmpty()) {
                    return new NewTopicsResponse(submittedTopics);
                }
                return provider.detectNewTopics(submittedTopics, candidates);
            } catch (Exception ex) {
                log.error("Provider {} failed dedup detection: {}", provider.getProviderName(), ex.getMessage());
            }
        }
        throw new RuntimeException("All AI providers failed during dedup detection");
    }

    public FlashcardResponse generateFlashcards(List<String> topics) {
        for (AIProvider provider : providers) {
            try {
                log.info("Using provider for flashcard generation: {}", provider.getProviderName());
                return provider.generateFlashcards(topics);
            } catch (Exception ex) {
                log.error("Provider {} failed flashcard generation: {}", provider.getProviderName(), ex.getMessage());
            }
        }
        throw new RuntimeException("All AI providers failed during flashcard generation");
    }
}