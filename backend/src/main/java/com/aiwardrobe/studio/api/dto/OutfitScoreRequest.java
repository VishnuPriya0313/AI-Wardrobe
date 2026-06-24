package com.aiwardrobe.studio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OutfitScoreRequest(
    @NotBlank
    @Pattern(regexp = "^data:image/(png|jpeg|jpg|webp);base64,.+", message = "Selected image must be a PNG, JPG, or WEBP data URL")
    String selectedImage,

    @NotBlank
    @Pattern(regexp = "^data:image/(png|jpeg|jpg|webp);base64,.+", message = "Candidate image must be a PNG, JPG, or WEBP data URL")
    String candidateImage,

    String selectedLabel,
    String candidateLabel
) {
}
