package com.anupam.reminiscence.ai_provider;

import com.anupam.reminiscence.ai_provider.AIProvider;
import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.dto.ai.GroqResponse; // Reusing your standardized Chat Completion DTO
import com.anupam.reminiscence.dto.ai.NewTopicsResponse;
import com.anupam.reminiscence.dto.ai.TopicsResponse;
import com.anupam.reminiscence.utils.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service("MISTRAL_AI")
@Order(6)
@RequiredArgsConstructor
public class MistralAIProvider implements AIProvider {

    @Value("${mistral.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Standardized reliable Mistral production models
    // Recommended options: "mistral-small-latest" (cost-optimized) or "mistral-large-latest" (highly capable)
    private static final String MODEL_NAME = "mistral-medium-latest";

    @Override
    public TopicsResponse extractTopics(String text) {
        try {
            String prompt = PromptBuilder.buildTopicExtractionPrompt(text);
            String raw = callMistralAI(prompt);
            return objectMapper.readValue(raw, TopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Mistral AI topic extraction failed", e);
        }
    }

    @Override
    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        try {
            String prompt = PromptBuilder.buildDeduplicationPrompt(submittedTopics, candidates);
            String raw = callMistralAI(prompt);
            return objectMapper.readValue(raw, NewTopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Mistral AI dedup detection failed", e);
        }
    }

    @Override
    public FlashcardResponse generateFlashcards(List<String> topics) {
        try {
            String prompt = PromptBuilder.buildFlashcardPrompt(topics);
            String raw = callMistralAI(prompt);
            return objectMapper.readValue(raw, FlashcardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Mistral AI flashcard generation failed", e);
        }
    }

    private String callMistralAI(String prompt) throws Exception {
        // Enforcing system instruction guidelines and native json_object response rules
        String requestJson = """
                {
                  "model": "%s",
                  "messages": [
                   {
                                     "role": "system",
                                     "content": "You are a rigid structural data extraction engine. You strictly honor line layouts, newline breaks, and pattern mirrors without inventing conversational text blocks."
                                   },
                    {
                      "role": "user",
                      "content": %s
                    }
                  ],
                  "response_format": {
                    "type": "json_object"
                  },
                  "temperature": 0.4
                }
                """.formatted(MODEL_NAME, objectMapper.writeValueAsString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mistral.ai/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Mistral AI API error code: " + response.statusCode() + " Body: " + response.body());
        }

        // Mapping response payload properties over the same structural DTO target
        GroqResponse mistralResponse = objectMapper.readValue(response.body(), GroqResponse.class);

        return mistralResponse.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .trim();
    }

    @Override
    public String getProviderName() {
        return "MISTRAL_AI";
    }
}