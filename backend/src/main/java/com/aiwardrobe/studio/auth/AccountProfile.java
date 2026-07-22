package com.aiwardrobe.studio.auth;

public record AccountProfile(String username, String email, boolean passwordSet, String signInMethod) {}
