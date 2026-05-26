package com.anupam.reminiscence.dto;

import com.anupam.reminiscence.constants.Level;
import lombok.Data;

@Data
public class ReviewRequest {
    private Level rating;
}