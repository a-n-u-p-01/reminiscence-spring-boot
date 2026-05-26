package com.anupam.reminiscence.dto.ai;

import lombok.Data;

@Data
public class Flashcard {
    private String conceptName;
    private String question;
    private String answer;
    private String notes;
}