package com.anupam.reminiscence.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateTopicsRequest {
    private List<String> topics;
}
