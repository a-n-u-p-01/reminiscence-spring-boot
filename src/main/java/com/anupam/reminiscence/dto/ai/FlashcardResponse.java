package com.anupam.reminiscence.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class FlashcardResponse {
    private List<Flashcard> flashcardList;
}
