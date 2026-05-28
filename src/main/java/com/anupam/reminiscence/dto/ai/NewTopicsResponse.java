package com.anupam.reminiscence.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class NewTopicsResponse {
    private List<String> newTopics;

    public NewTopicsResponse(List<String> newTopics) {
        this.newTopics = newTopics;
    }
}