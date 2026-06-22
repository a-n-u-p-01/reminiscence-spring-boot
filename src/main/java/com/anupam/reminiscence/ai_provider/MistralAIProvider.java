package com.anupam.reminiscence.ai_provider;

import com.anupam.reminiscence.dto.ai.*;
import com.anupam.reminiscence.utils.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service("MISTRAL_AI")
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class MistralAIProvider implements AIProvider {

    @Value("${mistral.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String MODEL_NAME = "mistral-medium-latest";

    // ---------- EXISTING METHODS (unchanged) ----------
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

    // ---------- NEW METHODS ----------
    @Override
    public String classifyTopic(String topic) {
        try {
            String prompt = PromptBuilder.buildClassificationPrompt(topic);
            String raw = callMistralAI(prompt, 0.1);
            JsonNode root = objectMapper.readTree(raw);
            String type = root.get("type").asText().toUpperCase();
            log.info("Classified topic '{}' as: {}", topic, type);
            return type;
        } catch (Exception e) {
            log.error("Classification failed for topic: {}, falling back to EXPLANATION", topic, e);
            return "EXPLANATION";
        }
    }

    @Override
    public Flashcard generateFlashcardWithType(String topic, String type) {
        try {
            String prompt = PromptBuilder.buildTypedFlashcardPrompt(topic, type);
            String raw = callMistralAI(prompt, 0.4);
            return objectMapper.readValue(raw, Flashcard.class);
        } catch (Exception e) {
            throw new RuntimeException("Typed flashcard generation failed for topic: " + topic, e);
        }
    }

    // ---------- OVERLOADED CALL METHOD (with temperature) ----------
    private String callMistralAI(String prompt, double temperature) throws Exception {
        String requestJson = """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "user",
                      "content": %s
                    }
                  ],
                  "response_format": {
                    "type": "json_object"
                  },
                  "temperature": %s
                }
                """.formatted(MODEL_NAME, objectMapper.writeValueAsString(prompt), temperature);

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

        GroqResponse mistralResponse = objectMapper.readValue(response.body(), GroqResponse.class);
        return mistralResponse.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .trim();
    }

    // ---------- ORIGINAL CALL METHOD (unchanged) ----------
    private String callMistralAI(String prompt) throws Exception {
        return callMistralAI(prompt, 0.4);
    }

    @Override
    public String getProviderName() {
        return "MISTRAL_AI";
    }
}