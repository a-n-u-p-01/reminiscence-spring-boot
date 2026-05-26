package com.anupam.reminiscence.dto.ai;

import lombok.Data;

import java.util.List;

@Data

public class NewTopicsResponse {
    private List<String> newTopics;

    public NewTopicsResponse(List<String> newTopics) {
        this.newTopics = newTopics;
    }
}