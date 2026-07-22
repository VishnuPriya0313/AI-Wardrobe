package com.aiwardrobe.studio.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record OutfitBatchRequest(
    @NotBlank String selectedLabel,
    String selectedImage,
    @NotEmpty @Size(max = 100) List<@Valid OutfitCandidate> candidates
) {
}
