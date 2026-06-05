package com.anupam.reminiscence.dto;

import lombok.Data;

import java.util.List;

@Data
public class DailyEntryRequest {
    private String text;
    private List<String> topics;
}