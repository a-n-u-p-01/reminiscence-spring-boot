package com.anupam.reminiscence.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserConceptRequest {
    private UUID conceptId;
    private String conceptName;
    private String question;
    private String mainNote;
    private String extraNote;
}
