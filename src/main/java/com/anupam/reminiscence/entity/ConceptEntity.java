package com.anupam.reminiscence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "concept", schema = "retention")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConceptEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;


    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    @Column(name = "key_notes")
    private String keyNotes;

    @Column(nullable = false)
    private String difficulty;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}