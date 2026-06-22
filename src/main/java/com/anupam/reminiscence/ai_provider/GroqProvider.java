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

@Service("GROQ")
@Order(6)
@RequiredArgsConstructor
@Slf4j
public class GroqProvider implements AIProvider {

    @Value("${groq.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ---------- EXISTING METHODS (unchanged) ----------
    @Override
    public TopicsResponse extractTopics(String text) {
        try {
            String prompt = PromptBuilder.buildTopicExtractionPrompt(text);
            String raw = callGroq(prompt);
            return objectMapper.readValue(raw, TopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Groq topic extraction failed", e);
        }
    }

    @Override
    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        try {
            String prompt = PromptBuilder.buildDeduplicationPrompt(submittedTopics, candidates);
            String raw = callGroq(prompt);
            return objectMapper.readValue(raw, NewTopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Groq dedup detection failed", e);
        }
    }

    @Override
    public FlashcardResponse generateFlashcards(List<String> topics) {
        try {
            String prompt = PromptBuilder.buildFlashcardPrompt(topics);
            String raw = callGroq(prompt);
            return objectMapper.readValue(raw, FlashcardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Groq flashcard generation failed", e);
        }
    }

    // ---------- ORIGINAL CALL METHOD (unchanged) ----------
    private String callGroq(String prompt) throws Exception {
        String requestJson = """
                {
                  "model": "openai/gpt-oss-120b",
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
                  "temperature": 0.3
                }
                """.formatted(objectMapper.writeValueAsString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        GroqResponse groqResponse =
                objectMapper.readValue(response.body(), GroqResponse.class);

        return groqResponse.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    // ---------- NEW METHODS ----------
    @Override
    public String classifyTopic(String topic) {
        try {
            String prompt = PromptBuilder.buildClassificationPrompt(topic);
            String raw = callGroq(prompt, 0.1);
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
            String raw = callGroq(prompt, 0.4);
            return objectMapper.readValue(raw, Flashcard.class);
        } catch (Exception e) {
            throw new RuntimeException("Typed flashcard generation failed for topic: " + topic, e);
        }
    }

    // ---------- NEW OVERLOADED CALL METHOD (with temperature) ----------
    private String callGroq(String prompt, double temperature) throws Exception {
        String requestJson = """
                {
                  "model": "openai/gpt-oss-120b",
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
                  "temperature": %s
                }
                """.formatted(objectMapper.writeValueAsString(prompt), temperature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.statusCode() + " - " + response.body());
        }

        GroqResponse groqResponse =
                objectMapper.readValue(response.body(), GroqResponse.class);

        return groqResponse.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }

    @Override
    public String getProviderName() {
        return "GROQ";
    }
}