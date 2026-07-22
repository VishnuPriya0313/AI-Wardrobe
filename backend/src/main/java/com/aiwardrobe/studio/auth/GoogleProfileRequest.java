package com.aiwardrobe.studio.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleProfileRequest(
    @NotBlank @Size(min = 3, max = 40)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain letters, numbers, dots, underscores, and hyphens.")
    String username,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(max = 128) String confirmPassword) {}
