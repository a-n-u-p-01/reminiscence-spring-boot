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

@Service("CLOUDFLARE")
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class CloudflareAIProvider implements AIProvider {

    @Value("${cloudflare.account.id}")
    private String accountId;

    @Value("${cloudflare.api.token}")
    private String apiToken;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String MODEL_NAME = "@cf/openai/gpt-oss-20b";

    // ---------- EXISTING METHODS (unchanged) ----------
    @Override
    public TopicsResponse extractTopics(String text) {
        try {
            String prompt = PromptBuilder.buildTopicExtractionPrompt(text);
            String raw = callCloudflare(prompt);
            return objectMapper.readValue(raw, TopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Cloudflare topic extraction failed", e);
        }
    }

    @Override
    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        try {
            String prompt = PromptBuilder.buildDeduplicationPrompt(submittedTopics, candidates);
            String raw = callCloudflare(prompt);
            return objectMapper.readValue(raw, NewTopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Cloudflare deduplication failed", e);
        }
    }

    @Override
    public FlashcardResponse generateFlashcards(List<String> topics) {
        try {
            String prompt = PromptBuilder.buildFlashcardPrompt(topics);
            String raw = callCloudflare(prompt);
            return objectMapper.readValue(raw, FlashcardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Cloudflare flashcard generation failed", e);
        }
    }

    // ---------- NEW METHODS (implemented) ----------
    @Override
    public String classifyTopic(String topic) {
        try {
            String prompt = PromptBuilder.buildClassificationPrompt(topic);
            // Use low temperature for deterministic classification
            String raw = callCloudflare(prompt, 0.1);
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
            String raw = callCloudflare(prompt, 0.4);
            // The prompt returns a single flashcard object (not wrapped in flashcardList)
            return objectMapper.readValue(raw, Flashcard.class);
        } catch (Exception e) {
            throw new RuntimeException("Typed flashcard generation failed for topic: " + topic, e);
        }
    }

    // ---------- OVERLOADED CALL METHOD (with temperature) ----------
    private String callCloudflare(String prompt, double temperature) throws Exception {
        String requestJson = """
            {
              "model": "%s",
              "messages": [
                {
                  "role": "system",
                  "content": "You are an expert teacher creating revision notes and flashcards; explain concepts with intuition first, then definition, using simple clear English, concise examples, beginner-friendly language, and memorable explanations instead of textbook definitions."
                },
                {
                  "role": "user",
                  "content": %s
                }
              ],
              "response_format": {"type": "json_object"},
              "max_tokens": 2024,
              "temperature": %s
            }
            """.formatted(MODEL_NAME, objectMapper.writeValueAsString(prompt), temperature);

        String url = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/chat/completions".formatted(accountId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Cloudflare AI Error " + response.statusCode() + ": " + response.body());
        }

        GroqResponse cfResponse = objectMapper.readValue(response.body(), GroqResponse.class);
        return cfResponse.getChoices().get(0).getMessage().getContent().trim();
    }

    // ---------- ORIGINAL CALL METHOD (unchanged) ----------
    private String callCloudflare(String prompt) throws Exception {
        return callCloudflare(prompt, 0.2);
    }

    @Override
    public String getProviderName() {
        return "CLOUDFLARE";
    }
}