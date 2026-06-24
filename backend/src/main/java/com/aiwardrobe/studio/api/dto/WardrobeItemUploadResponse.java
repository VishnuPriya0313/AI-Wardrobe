package com.aiwardrobe.studio.api.dto;

public record WardrobeItemUploadResponse(
    boolean stored,
    String bucket,
    String itemKey,
    String imageKey,
    String metadataKey,
    String imageUrl
) {
}
