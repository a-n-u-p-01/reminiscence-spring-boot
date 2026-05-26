package com.anupam.reminiscence.dto.ai;

import lombok.Data;
import java.util.List;

@Data
public class GeminiRequest {

    private List<Content> contents;

    public GeminiRequest(String prompt) {
        this.contents = List.of(new Content(prompt));
    }

    @Data
    static class Content {
        private List<Part> parts;

        public Content(String text) {
            this.parts = List.of(new Part(text));
        }
    }

    @Data
    static class Part {
        private String text;

        public Part(String text) {
            this.text = text;
        }
    }
}