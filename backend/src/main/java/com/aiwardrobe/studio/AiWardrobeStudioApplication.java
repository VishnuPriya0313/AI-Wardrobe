package com.aiwardrobe.studio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiWardrobeStudioApplication {

  public static void main(String[] args) {
    loadDotEnv();
    SpringApplication.run(AiWardrobeStudioApplication.class, args);
  }

  private static void loadDotEnv() {
    List<Path> envPaths = List.of(Path.of(".env"), Path.of("..", ".env"));

    try {
      for (Path envPath : envPaths) {
        if (!Files.exists(envPath)) {
          continue;
        }
        for (String line : Files.readAllLines(envPath)) {
          String trimmed = line.trim();
          if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            continue;
          }
          int separator = trimmed.indexOf('=');
          if (separator < 1) {
            continue;
          }
          String key = trimmed.substring(0, separator).trim();
          String value = trimmed.substring(separator + 1).trim();
          if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
          }
        }
      }
    } catch (IOException ignored) {
      // Environment variables still work if the local .env file cannot be read.
    }
  }
}
