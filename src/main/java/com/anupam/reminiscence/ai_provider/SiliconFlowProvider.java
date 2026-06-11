package com.anupam.reminiscence.ai_provider;

import com.anupam.reminiscence.dto.ai.FlashcardResponse;
import com.anupam.reminiscence.dto.ai.GroqResponse; // Reused or map to a generic AIResponse if needed
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

@Service("SILICONFLOW")
@Order(1)
@RequiredArgsConstructor
public class SiliconFlowProvider implements AIProvider {

    @Value("${siliconflow.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Utilizing the ultra-cost-efficient DeepSeek Flash architecture on SiliconFlow
//    private static final String MODEL_NAME = "deepseek-ai/DeepSeek-V4-Flash";

    private static final String MODEL_NAME = "deepseek-ai/DeepSeek-V4-Flash";

    @Override
    public TopicsResponse extractTopics(String text) {
        try {
            String prompt = PromptBuilder.buildTopicExtractionPrompt(text);
            String raw = callSiliconFlow(prompt);
            return objectMapper.readValue(raw, TopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("SiliconFlow topic extraction failed", e);
        }
    }

    @Override
    public NewTopicsResponse detectNewTopics(List<String> submittedTopics, List<String> candidates) {
        try {
            String prompt = PromptBuilder.buildDeduplicationPrompt(submittedTopics, candidates);
            String raw = callSiliconFlow(prompt);
            return objectMapper.readValue(raw, NewTopicsResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("SiliconFlow dedup detection failed", e);
        }
    }

    @Override
    public FlashcardResponse generateFlashcards(List<String> topics) {
        try {
            String prompt = PromptBuilder.buildFlashcardPrompt(topics);
            String raw = callSiliconFlow(prompt);
            return objectMapper.readValue(raw, FlashcardResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("SiliconFlow flashcard generation failed", e);
        }
    }

    private String callSiliconFlow(String prompt) throws Exception {
        // Enforcing JSON Mode by passing response_format down to the hardware engine
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
                  "temperature": 0.4
                }
                """.formatted(MODEL_NAME, objectMapper.writeValueAsString(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.siliconflow.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("SiliconFlow API error code: " + response.statusCode() + " Body: " + response.body());
        }

        // Mapping response via your existing GroqResponse structure if fields match (id, choices, etc.)
        GroqResponse sfResponse = objectMapper.readValue(response.body(), GroqResponse.class);

        return sfResponse.getChoices()
                .get(0)
                .getMessage()
                .getContent()
                .trim();
    }

    @Override
    public String getProviderName() {
        return "SILICONFLOW";
    }
}