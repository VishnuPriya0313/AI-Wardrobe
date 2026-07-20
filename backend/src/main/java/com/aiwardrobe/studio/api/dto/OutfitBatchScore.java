package com.aiwardrobe.studio.api.dto;

public record OutfitBatchScore(
    String candidateId,
    int score,
    String verdict
) {
}
