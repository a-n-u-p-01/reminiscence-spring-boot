package com.anupam.reminiscence.dto.ai;

import lombok.Data;

@Data
public class QuestionResponse {
    private String question;
    private String answer;
    private String notes;
}