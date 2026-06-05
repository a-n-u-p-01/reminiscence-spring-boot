package com.anupam.reminiscence.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ConceptExplorerResponse {
    private List<ConceptItemDTO> data;
    private int currentPage;
    private int totalPages;
    private long totalItems;
}