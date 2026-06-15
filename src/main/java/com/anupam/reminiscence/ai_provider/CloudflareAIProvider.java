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

@Service("CLOUDFLARE")
@Order(2)
@RequiredArgsConstructor
public class CloudflareAIProvider implements AIProvider {

    @Value("${cloudflare.account.id}") // Found in your Cloudflare dashboard URL
    private String accountId;

    @Value("${cloudflare.api.token}") // Generated under User Profile -> API Tokens -> Workers AI
    private String apiToken;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // High performance model highly optimized for low neuron consumption
    private static final String MODEL_NAME = "@cf/openai/gpt-oss-20b";

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

    private String callCloudflare(String prompt) throws Exception {
        // FIX: Include the "model" field in the JSON structure
        String requestJson = """
            {
              "model": "%s",
              "messages": [
               {
                     "role": "system",
                      "content": "You are an expert teacher creating revision notes and flashcards; explain concepts with intuition first, then definition, using simple clear English, concise examples, beginner-friendly language, and memorable explanations instead of textbook definitions."
                     },
              {"role": "user", "content": %s}
              ],
              "response_format": {"type": "json_object"},
              "max_tokens": 2024,
              "temperature": 0.2
            }
            """.formatted(MODEL_NAME, objectMapper.writeValueAsString(prompt));

        String url = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/chat/completions".formatted(accountId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken)
                // Workers AI often requires an AI Gateway ID for the REST API
                // Try adding this header if it continues to fail
                // .header("cf-aig-gateway-id", "default")
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

    @Override
    public String getProviderName() {
        return "CLOUDFLARE";
    }
}