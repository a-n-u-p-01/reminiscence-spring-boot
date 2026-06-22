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

@Service("GEMINI")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class GeminiProvider implements AIProvider {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ---------- EXISTING METHODS (unchanged) ----------
    @Override
    public TopicsResponse extractTopics(String text) {
        try {
            String prompt = PromptBuilder.buildTopicExtractionPrompt(text);
            String raw = callGemini(prompt);
            return objectMapper.readValue(raw, TopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini topic extraction failed", e);
        }
    }

    @Override
    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        try {
            String prompt = PromptBuilder.buildDeduplicationPrompt(submittedTopics, candidates);
            String raw = callGemini(prompt);
            return objectMapper.readValue(raw, NewTopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini dedup detection failed", e);
        }
    }

    @Override
    public FlashcardResponse generateFlashcards(List<String> topics) {
        try {
            String prompt = PromptBuilder.buildFlashcardPrompt(topics);
            String raw = callGemini(prompt);
            return objectMapper.readValue(raw, FlashcardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Gemini flashcard generation failed", e);
        }
    }

    // ---------- ORIGINAL CALL METHOD (unchanged) ----------
    private String callGemini(String prompt) throws Exception {
        GeminiRequest requestBody = new GeminiRequest(prompt);
        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"
                ))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        GeminiApiResponse apiResponse =
                objectMapper.readValue(response.body(), GeminiApiResponse.class);

        return apiResponse.getCandidates()
                .get(0)
                .getContent()
                .getParts()
                .get(0)
                .getText()
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    // ---------- NEW METHODS (added without modifying existing) ----------
    @Override
    public String classifyTopic(String topic) {
        try {
            String prompt = PromptBuilder.buildClassificationPrompt(topic);
            String raw = callGemini(prompt, 0.1);
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
            String raw = callGemini(prompt, 0.4);
            return objectMapper.readValue(raw, Flashcard.class);
        } catch (Exception e) {
            throw new RuntimeException("Typed flashcard generation failed for topic: " + topic, e);
        }
    }

    // ---------- NEW OVERLOADED CALL METHOD (supports temperature) ----------
    private String callGemini(String prompt, double temperature) throws Exception {
        // Build request JSON manually so we don't touch the existing GeminiRequest class
        String requestJson = """
                {
                  "contents": [
                    {
                      "parts": [
                        {
                          "text": %s
                        }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": %s,
                    "response_mime_type": "application/json"
                  }
                }
                """.formatted(objectMapper.writeValueAsString(prompt), temperature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"
                ))
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }

        GeminiApiResponse apiResponse =
                objectMapper.readValue(response.body(), GeminiApiResponse.class);

        return apiResponse.getCandidates()
                .get(0)
                .getContent()
                .getParts()
                .get(0)
                .getText()
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    @Override
    public String getProviderName() {
        return "GEMINI";
    }
}