package com.anupam.reminiscence.dto.ai;

import lombok.Data;

@Data
public class GeminiResponse {
    private String conceptName;
    private String question;
    private String answer;
    private String notes;
}