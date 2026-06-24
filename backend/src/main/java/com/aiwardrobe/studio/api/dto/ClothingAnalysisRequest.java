package com.aiwardrobe.studio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClothingAnalysisRequest(
    @NotBlank
    @Pattern(regexp = "^data:image/(png|jpeg|jpg|webp);base64,.+", message = "Image must be a PNG, JPG, or WEBP data URL")
    String image
) {
}
