package com.aiwardrobe.studio.api;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import com.aiwardrobe.studio.ai.WardrobeAiService;
import com.aiwardrobe.studio.api.dto.ClothingAnalysis;
import com.aiwardrobe.studio.api.dto.ClothingAnalysisRequest;
import com.aiwardrobe.studio.api.dto.HealthResponse;
import com.aiwardrobe.studio.api.dto.OutfitScore;
import com.aiwardrobe.studio.api.dto.OutfitScoreRequest;
import com.aiwardrobe.studio.api.dto.OutfitBatchRequest;
import com.aiwardrobe.studio.api.dto.OutfitBatchResponse;
import com.aiwardrobe.studio.api.dto.ShoppingOption;
import com.aiwardrobe.studio.api.dto.ShoppingRequest;
import com.aiwardrobe.studio.api.dto.WardrobeItemUploadRequest;
import com.aiwardrobe.studio.api.dto.WardrobeItemUploadResponse;
import com.aiwardrobe.studio.storage.WardrobeStorageService;
import com.aiwardrobe.studio.shopping.ShoppingSearchService;
import com.aiwardrobe.studio.auth.AppUserRepository;

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
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api")
public class WardrobeAiController {

  private final WardrobeAiService aiService;
  private final WardrobeStorageService storageService;
  private final ShoppingSearchService shoppingSearchService;
  private final AppUserRepository users;

  public WardrobeAiController(
          WardrobeAiService aiService,
          WardrobeStorageService storageService,
          ShoppingSearchService shoppingSearchService,
          AppUserRepository users) {
    this.aiService = aiService;
    this.storageService = storageService;
    this.shoppingSearchService = shoppingSearchService;
    this.users = users;
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

  @PostMapping("/score-outfits")
  public OutfitBatchResponse scoreOutfits(@Valid @RequestBody OutfitBatchRequest request) {
    return aiService.scoreOutfits(request);
  }

  @PostMapping("/shopping-options")
  public List<ShoppingOption> shoppingOptions(@Valid @RequestBody ShoppingRequest request) {
    String query = aiService.createShoppingQuery(request.selectedItem(), request.targetCategory(), request.targetType());
    return shoppingSearchService.search(query, request.targetCategory(), request.targetType(), request.selectedItem());
  }

  @PostMapping("/wardrobe-items")
  public WardrobeItemUploadResponse storeWardrobeItem(@Valid @RequestBody WardrobeItemUploadRequest request, Authentication auth) {
    return storageService.storeWardrobeItem(userId(auth), request);
  }

  @GetMapping("/wardrobe-items")
  public List<Map<String, Object>> listWardrobeItems(Authentication auth) {
    return storageService.listWardrobeItems(userId(auth));
  }

  @DeleteMapping("/wardrobe-items/{id}")
  public ResponseEntity<Map<String, Boolean>> deleteWardrobeItem(@PathVariable String id, Authentication auth) {
    storageService.deleteWardrobeItem(userId(auth), id);
    return ResponseEntity.ok(Map.of("deleted", true));
  }

  private UUID userId(Authentication auth) {
    return users.findByUsernameIgnoreCase(auth.getName())
        .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists."))
        .getId();
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
