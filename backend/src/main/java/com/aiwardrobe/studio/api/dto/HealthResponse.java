package com.aiwardrobe.studio.api.dto;

public record HealthResponse(
    boolean ok,
    String provider,
    boolean configured,
    String model
) {
}
