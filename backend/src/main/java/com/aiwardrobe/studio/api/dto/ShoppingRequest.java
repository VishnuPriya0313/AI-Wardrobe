package com.aiwardrobe.studio.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShoppingRequest(
    @NotBlank String selectedItem,
    @NotBlank
    @Pattern(regexp = "top|bottom", message = "Target category must be top or bottom")
    String targetCategory,
    @NotBlank
    @Pattern(
        regexp = "any-top|blouse|shirt|t-shirt|any-bottom|pants|skirt|shorts",
        message = "Invalid shopping garment type")
    String targetType
) {
}
