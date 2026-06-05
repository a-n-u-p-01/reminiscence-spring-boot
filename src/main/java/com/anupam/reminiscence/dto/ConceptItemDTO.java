package com.anupam.reminiscence.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ConceptItemDTO {
    private UUID conceptId;
    private String conceptName;
    private int masteryScore;
    private int reviewCount;
    private int failureCount;
    private String mainNote;
    private String extraNote;
    private Instant createdAt;
}