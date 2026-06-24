package com.aiwardrobe.studio.api;

import java.util.Map;
import java.util.List;

import com.aiwardrobe.studio.ai.WardrobeAiService;
import com.aiwardrobe.studio.api.dto.ClothingAnalysis;
import com.aiwardrobe.studio.api.dto.ClothingAnalysisRequest;
import com.aiwardrobe.studio.api.dto.HealthResponse;
import com.aiwardrobe.studio.api.dto.OutfitScore;
import com.aiwardrobe.studio.api.dto.OutfitScoreRequest;
import com.aiwardrobe.studio.api.dto.WardrobeItemUploadRequest;
import com.aiwardrobe.studio.api.dto.WardrobeItemUploadResponse;
import com.aiwardrobe.studio.storage.WardrobeStorageService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WardrobeAiController {

  private final WardrobeAiService aiService;
  private final WardrobeStorageService storageService;

  public WardrobeAiController(
          WardrobeAiService aiService,
          WardrobeStorageService storageService) {
    this.aiService = aiService;
    this.storageService = storageService;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse(true, aiService.providerName(), aiService.isConfigured(), aiService.activeModelName());
  }

  @PostMapping("/analyze-clothing")
  public ClothingAnalysis analyzeClothing(@Valid @RequestBody ClothingAnalysisRequest request) {
    return aiService.analyzeClothing(request.image());
  }

  @PostMapping("/score-outfit")
  public OutfitScore scoreOutfit(@Valid @RequestBody OutfitScoreRequest request) {
    return aiService.scoreOutfit(request);
  }

  @PostMapping("/wardrobe-items")
  public WardrobeItemUploadResponse storeWardrobeItem(@Valid @RequestBody WardrobeItemUploadRequest request) {
    return storageService.storeWardrobeItem(request);
  }

  @GetMapping("/wardrobe-items")
  public List<Map<String, Object>> listWardrobeItems() {
    return storageService.listWardrobeItems();
  }

  @DeleteMapping("/wardrobe-items/{id}")
  public ResponseEntity<Map<String, Boolean>> deleteWardrobeItem(@PathVariable String id) {
    storageService.deleteWardrobeItem(id);
    return ResponseEntity.ok(Map.of("deleted", true));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException error) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", error.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException error) {
    return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationError(MethodArgumentNotValidException error) {
    FieldError fieldError = error.getBindingResult().getFieldError();
    String message = fieldError == null ? "Invalid request." : fieldError.getDefaultMessage();
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }
}
