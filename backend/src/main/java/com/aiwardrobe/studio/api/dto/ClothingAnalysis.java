package com.aiwardrobe.studio.api.dto;

public record ClothingAnalysis(
    String name,
    String color,
    String category,
    String pattern,
    String material,
    String occasion,
    String season
) {
}
