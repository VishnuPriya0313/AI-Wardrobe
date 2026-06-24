package com.aiwardrobe.studio.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WardrobeItemUploadRequest(
    @NotBlank
    String id,

    @NotBlank
    @Pattern(regexp = "^data:image/(png|jpeg|jpg|webp);base64,.+", message = "Image must be a PNG, JPG, or WEBP data URL")
    String image,

    String imageFingerprint,
    String originalFileName,

    @NotNull
    @Valid
    ClothingAnalysis analysis,

    @NotBlank
    String category,

    String createdAt
) {
}
