package com.anupam.reminiscence.ai_provider;

import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.dto.ai.GroqResponse;
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

@Service("GITHUBMODELS")
@Order(4)
@RequiredArgsConstructor
public class GitHubModelsProvider implements AIProvider {

    @Value("${github.models.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // The exact model ID slug as per GitHub Marketplace
    private static final String MODEL_NAME = "openai/gpt-4.1";

    // CORRECT ENDPOINT FOR GITHUB MODELS
    private static final String API_URL = "https://models.github.ai/inference/chat/completions";

    @Override
    public TopicsResponse extractTopics(String text) {
        try {
            String prompt = PromptBuilder.buildTopicExtractionPrompt(text);
            String raw = callGitHubModels(prompt);
            return objectMapper.readValue(raw, TopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("GitHub Models topic extraction failed", e);
        }
    }

    @Override
    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        try {
            String prompt = PromptBuilder.buildDeduplicationPrompt(submittedTopics, candidates);
            String raw = callGitHubModels(prompt);
            return objectMapper.readValue(raw, NewTopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("GitHub Models dedup detection failed", e);
        }
    }

    @Override
    public FlashcardResponse generateFlashcards(List<String> topics) {
        try {
            String prompt = PromptBuilder.buildFlashcardPrompt(topics);
            String raw = callGitHubModels(prompt);
            return objectMapper.readValue(raw, FlashcardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("GitHub Models flashcard generation failed", e);
        }
    }

    private String callGitHubModels(String prompt) throws Exception {
        String requestJson = """
                {
                  "model": "%s",
                  "messages": [
                   {
                     "role": "system",
                      "content": "You are a friendly, expert human tutor. Your goal is to help a student learn. Use simple, conversational language and analogies."
                     },
                    {
                      "role": "user",
                      "content": %s
                    }
                  ],
                  "response_format": {
                    "type": "json_object"
                  },
                  "temperature": 0.3
                }
                """.formatted(MODEL_NAME, objectMapper.writeValueAsString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                // MANDATORY HEADER FOR GITHUB MODELS API
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub Models Error! Status: " + response.statusCode() + " Body: " + response.body());
        }

        GroqResponse ghResponse = objectMapper.readValue(response.body(), GroqResponse.class);

        return ghResponse.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .trim();
    }

    @Override
    public String getProviderName() {
        return "GITHUBMODELS";
    }
}