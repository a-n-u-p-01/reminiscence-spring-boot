package com.anupam.reminiscence.ai_provider;

import com.anupam.reminiscence.dto.ai.Flashcard;
import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.dto.ai.NewTopicsResponse;
import com.anupam.reminiscence.dto.ai.TopicsResponse;

import java.util.List;

public interface AIProvider {

    // AI Call 0 — extract topics from free text
    TopicsResponse extractTopics(String text);

    // AI Call 1 — semantic duplicate detection
    NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates);

    // AI Call 2 — flashcard generation
    FlashcardResponse generateFlashcards(List<String> topics);

    String getProviderName();
    Flashcard generateFlashcardWithType(String topic, String type);

    String classifyTopic(String topic);
}