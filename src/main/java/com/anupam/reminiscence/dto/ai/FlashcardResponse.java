package com.anupam.reminiscence.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class FlashcardResponse {
    private List<Flashcard> flashcardList;
}
