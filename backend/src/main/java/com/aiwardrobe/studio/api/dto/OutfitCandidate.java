package com.aiwardrobe.studio.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OutfitCandidate(
    @NotBlank String id,
    @NotBlank String label,
    String image
) {
}
