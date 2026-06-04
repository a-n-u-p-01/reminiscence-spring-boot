package com.anupam.reminiscence.dto;

import com.anupam.reminiscence.constants.RecallRating;
import lombok.Data;

@Data
public class ReviewRequest {
    private RecallRating rating;
}