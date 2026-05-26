package com.anupam.reminiscence.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptReviewResponse {
    private UUID userConceptId;
    private UUID conceptId;
    private String name;
    private String questionText;
    private String answerText;
    private String keyNotes;
    private String difficulty;
    private Integer masteryScore;
    private Integer currentIntervalDays;
    private LocalDate nextReviewDate;
}