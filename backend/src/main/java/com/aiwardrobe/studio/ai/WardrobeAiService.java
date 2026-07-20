package com.aiwardrobe.studio.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aiwardrobe.studio.api.dto.ClothingAnalysis;
import com.aiwardrobe.studio.api.dto.OutfitScore;
import com.aiwardrobe.studio.api.dto.OutfitScoreRequest;
import com.aiwardrobe.studio.api.dto.OutfitBatchRequest;
import com.aiwardrobe.studio.api.dto.OutfitBatchResponse;
import com.aiwardrobe.studio.api.dto.OutfitBatchScore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class WardrobeAiService {

  private static final int MAX_PROMPT_TEXT = 800;

  private final RestClient openAiClient;
  private final RestClient ollamaClient;
  private final ObjectMapper objectMapper;
  private final String aiProvider;
  private final String openAiApiKey;
  private final String openAiModel;
  private final String ollamaModel;
  private final String ollamaMatchModel;
  private final String fashionTrendContext;

  public WardrobeAiService(
      ObjectMapper objectMapper,
      @Value("${ai.provider}") String aiProvider,
      @Value("${OPENAI_API_KEY:}") String openAiApiKey,
      @Value("${ai.openai.model}") String openAiModel,
      @Value("${ai.ollama.base-url}") String ollamaBaseUrl,
      @Value("${ai.ollama.model}") String ollamaModel,
      @Value("${ai.ollama.match-model}") String ollamaMatchModel,
      @Value("${fashion.trend-context:}") String fashionTrendContext) {
    this.objectMapper = objectMapper;
    this.aiProvider = aiProvider;
    this.openAiApiKey = openAiApiKey;
    this.openAiModel = openAiModel;
    this.ollamaModel = ollamaModel;
    this.ollamaMatchModel = ollamaMatchModel;
    this.fashionTrendContext = fashionTrendContext;
    this.openAiClient = RestClient.builder()
        .baseUrl("https://api.openai.com/v1")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
    this.ollamaClient = RestClient.builder()
        .baseUrl(ollamaBaseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  public ClothingAnalysis analyzeClothing(String imageDataUrl) {
    return switch (providerName()) {
      case "openai" -> analyzeClothingWithOpenAi(imageDataUrl);
      case "ollama" -> analyzeClothingWithOllama(imageDataUrl);
      default -> throw new IllegalStateException("Unsupported AI_PROVIDER: " + aiProvider);
    };
  }

  public OutfitScore scoreOutfit(OutfitScoreRequest request) {
    return switch (providerName()) {
      case "openai" -> scoreOutfitWithOpenAi(request);
      case "ollama" -> scoreOutfitWithOllama(request);
      default -> throw new IllegalStateException("Unsupported AI_PROVIDER: " + aiProvider);
    };
  }

  public OutfitBatchResponse scoreOutfits(OutfitBatchRequest request) {
    String response = switch (providerName()) {
      case "openai" -> scoreOutfitsWithOpenAi(request);
      case "ollama" -> callOllama(
          ollamaMatchModel,
          outfitBatchPrompt(request),
          List.of(),
          outfitBatchSchema(),
          Math.min(4096, 160 + request.candidates().size() * 120));
      default -> throw new IllegalStateException("Unsupported AI_PROVIDER: " + aiProvider);
    };
    return parseBatchScores(response, request);
  }

  public String createShoppingQuery(String selectedItem, String targetCategory, String targetType) {
    String requiredGarment = switch (targetType) {
      case "any-top" -> "women's top, blouse, or shirt";
      case "any-bottom" -> "women's pants, skirt, or shorts";
      case "t-shirt" -> "women's t-shirt";
      default -> "women's " + targetType;
    };
    String prompt = """
        Create concise style keywords for a clothing item that forms an excellent outfit
        with the selected wardrobe item below. Base the recommendation specifically on its garment
        type, exact color, silhouette, pattern, material, occasion, and season.
        The required shopping category is: %s.
        Include complementary color/material/style words that would look good with the selected item.
        Prefer wearable standalone basics over novelty, embellished, costume, or statement pieces.
        For casual spring or summer outfits, favor breathable fabrics and clean solid colors.
        Never recommend the same garment category as the selected item.
        Recommend one standalone garment only. Do not search for suits, matching sets,
        coordinated outfits, jumpsuits, dresses, jackets, cardigans, costumes, or multi-piece products.
        Return only structured JSON containing the query.
        Selected wardrobe item: %s
        """.formatted(requiredGarment, promptText(selectedItem));

    String response = switch (providerName()) {
      case "openai" -> {
        ensureOpenAiConfigured();
        Map<String, Object> body = Map.of(
            "model", openAiModel,
            "input", List.of(userMessage(Map.of("type", "input_text", "text", prompt))),
            "text", Map.of("format", jsonSchemaFormat("shopping_query", shoppingQuerySchema())));
        yield callOpenAi(body);
      }
      case "ollama" -> callOllama(ollamaMatchModel, prompt, List.of(), shoppingQuerySchema(), 160);
      default -> throw new IllegalStateException("Unsupported AI_PROVIDER: " + aiProvider);
    };

    try {
      String query = objectMapper.readTree(extractJson(response)).path("query").asText("").trim();
      if (query.isBlank()) throw new IllegalStateException("AI returned an empty shopping query.");
      query = removeWrongGarmentTerms(query, targetCategory);
      query = removeShoppingProductNoise(query);
      return requiredGarment + " " + query;
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("AI returned an invalid shopping query.");
    }
  }

  private String removeWrongGarmentTerms(String query, String targetCategory) {
    String wrongTerms = "top".equals(targetCategory)
        ? "(?i)\\b(pants?|trousers?|jeans?|skirts?|shorts?|bottoms?|palazzo|jackets?|cardigans?|sets?)\\b"
        : "(?i)\\b(tops?|blouses?|shirts?|tees?|t-shirts?|sweaters?|sweatshirts?|bodysuits?|jackets?|cardigans?|sets?)\\b";
    return query.replaceAll(wrongTerms, " ").replaceAll("\\s+", " ").trim();
  }

  private String removeShoppingProductNoise(String query) {
    return query
        .replaceAll("(?i)\\b(suits?|sets?|two[- ]piece|2[- ]piece|2pcs|outfits?|jackets?|cardigans?|costumes?)\\b", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public String providerName() {
    return String.valueOf(aiProvider).trim().toLowerCase();
  }

  public String activeModelName() {
    return "ollama".equals(providerName()) ? ollamaModel + " / " + ollamaMatchModel : openAiModel;
  }

  public boolean isConfigured() {
    return "ollama".equals(providerName()) || hasOpenAiKey();
  }

  private ClothingAnalysis analyzeClothingWithOpenAi(String imageDataUrl) {
    ensureOpenAiConfigured();
    Map<String, Object> body = Map.of(
        "model", openAiModel,
        "input", List.of(userMessage(
            Map.of("type", "input_text", "text", clothingAnalysisPrompt()),
            Map.of("type", "input_image", "image_url", imageDataUrl))),
        "text", Map.of("format", jsonSchemaFormat("clothing_analysis", clothingAnalysisSchema())));

    return normalizeClothing(parseJson(callOpenAi(body), ClothingAnalysis.class, "OpenAI"));
  }

  private OutfitScore scoreOutfitWithOpenAi(OutfitScoreRequest request) {
    ensureOpenAiConfigured();
    Map<String, Object> body = Map.of(
        "model", openAiModel,
        "input", List.of(userMessage(
            Map.of("type", "input_text", "text", outfitScorePrompt(request, true)),
            Map.of("type", "input_image", "image_url", request.selectedImage()),
            Map.of("type", "input_image", "image_url", request.candidateImage()))),
        "text", Map.of("format", jsonSchemaFormat("outfit_score", outfitScoreSchema())));

    return normalizeScore(parseJson(callOpenAi(body), OutfitScore.class, "OpenAI"));
  }

  private String scoreOutfitsWithOpenAi(OutfitBatchRequest request) {
    ensureOpenAiConfigured();
    Map<String, Object> body = Map.of(
        "model", openAiModel,
        "input", List.of(userMessage(
            Map.of("type", "input_text", "text", outfitBatchPrompt(request)))),
        "text", Map.of("format", jsonSchemaFormat("outfit_scores", outfitBatchSchema())));
    return callOpenAi(body);
  }

  private ClothingAnalysis analyzeClothingWithOllama(String imageDataUrl) {
    String response = callOllama(
        ollamaModel,
        clothingAnalysisPrompt(),
        List.of(base64Payload(imageDataUrl)),
        clothingAnalysisSchema());

    try {
      return normalizeClothing(parseJson(extractJson(response), ClothingAnalysis.class, "Ollama"));
    } catch (IllegalStateException error) {
      return fallbackClothingAnalysis(response);
    }
  }

  private OutfitScore scoreOutfitWithOllama(OutfitScoreRequest request) {
    String response = callOllama(ollamaMatchModel, outfitScorePrompt(request, false), List.of(), outfitScoreSchema());
    return normalizeScore(parseJson(extractJson(response), OutfitScore.class, "Ollama"));
  }

  private String callOpenAi(Map<String, Object> body) {
    JsonNode result;
    try {
      result = openAiClient.post().uri("/responses").body(body).retrieve().body(JsonNode.class);
    } catch (RestClientResponseException error) {
      throw new IllegalStateException(providerError(error, "OpenAI"));
    }

    String text = openAiOutputText(result);
    if (text.isBlank()) {
      throw new IllegalStateException("OpenAI returned no usable JSON.");
    }
    return text;
  }

  private String callOllama(String model, String prompt, List<String> images, Map<String, Object> schema) {
    return callOllama(model, prompt, images, schema, 220);
  }

  private String callOllama(
      String model,
      String prompt,
      List<String> images,
      Map<String, Object> schema,
      int maxOutputTokens) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("prompt", prompt);
    body.put("stream", false);
    body.put("format", schema);
    body.put("options", Map.of("temperature", 0.1, "num_predict", maxOutputTokens));
    if (!images.isEmpty()) {
      body.put("images", images);
    }

    JsonNode result;
    try {
      result = ollamaClient.post().uri("/api/generate").body(body).retrieve().body(JsonNode.class);
    } catch (RestClientResponseException error) {
      throw new IllegalStateException(providerError(error, "Ollama"));
    } catch (Exception error) {
      throw new IllegalStateException("Ollama request failed. Make sure Ollama is installed, running, and the model is pulled.");
    }

    String response = result == null ? "" : result.path("response").asText("");
    if (response.isBlank()) {
      throw new IllegalStateException("Ollama returned no usable JSON.");
    }
    return response;
  }

  private String clothingAnalysisPrompt() {
    return """
        Identify the single most prominent clothing item in this image.
        Ignore accessories, shoes, bags, background, and body parts.
        Return compact JSON matching the schema:
        name: product-style name with color, fit, and garment type
        color: specific color name
        category: top, bottom, or dress
        Use dress for dresses, jumpsuits, rompers, and other one-piece outfits.
        pattern: solid, striped, floral, plaid, checked, polka dot, graphic, lace, ribbed, or unknown
        material: cotton, linen, denim, knit, ribbed knit, chiffon, satin, leather, wool, polyester, or unknown
        occasion: casual, smart casual, work, formal, party, lounge, athletic, or beach
        season: spring, summer, fall, winter, spring/summer, fall/winter, or all season
        """;
  }

  private String outfitScorePrompt(OutfitScoreRequest request, boolean includeTrendContext) {
    return """
        Score how well these two clothing items work together as an outfit from 0 to 100.
        Consider color harmony, silhouette balance, formality, material, and occasion.
        %s
        Return compact JSON with score and a one-sentence verdict under 120 characters.
        Selected item: %s
        Candidate item: %s
        """.formatted(
        trendPromptLine(includeTrendContext),
        promptText(request.selectedLabel()),
        promptText(request.candidateLabel()));
  }

  private String outfitBatchPrompt(OutfitBatchRequest request) {
    StringBuilder candidates = new StringBuilder();
    request.candidates().forEach(candidate -> candidates
        .append("\n- ID ").append(promptText(candidate.id()))
        .append(": ").append(promptText(candidate.label())));
    return """
        Score every candidate against the selected clothing item as an outfit from 0 to 100.
        Consider color harmony, silhouette balance, formality, material, season, and occasion.
        First verify garment compatibility. A dress, jumpsuit, romper, or other one-piece outfit
        must never be matched as a top with pants, jeans, shorts, or a skirt.
        Judge the actual named garment type, not just its color.
        Use this strict scale:
        90-100: exceptional, editorial-level pairing with no meaningful conflict.
        75-89: strong and clearly coordinated.
        60-74: wearable, but has a noticeable color, silhouette, or formality issue.
        40-59: weak pairing with multiple conflicts.
        0-39: clear clash.
        Do not award 90+ merely because both items are casual or from the same color family.
        Penalize near-but-not-matching warm colors such as brown with bright red/orange.
        Penalize competing volume, such as a voluminous puff/peplum top with very wide bottoms.
        %s
        Return exactly one result for each candidate ID. Keep each verdict under 120 characters.
        Selected item: %s
        Candidates:%s
        """.formatted(
        trendPromptLine(true),
        promptText(request.selectedLabel()),
        candidates);
  }

  private OutfitBatchResponse parseBatchScores(String text, OutfitBatchRequest request) {
    try {
      JsonNode root = objectMapper.readTree(extractJson(text));
      Map<String, OutfitBatchScore> byId = new LinkedHashMap<>();
      int resultIndex = 0;
      for (JsonNode result : root.path("results")) {
        String id = result.path("candidateId").asText("");
        String verdict = result.path("verdict").asText("");
        int rawScore = result.path("score").asInt(0);
        if (rawScore == 0 && verdict.trim().matches("\\d{1,3}")) {
          rawScore = Math.max(0, Math.min(100, Integer.parseInt(verdict.trim())));
          verdict = scoreBandVerdict(rawScore);
        }
        String reportedId = id;
        if (request.candidates().stream().noneMatch(candidate -> candidate.id().equals(reportedId))
            && resultIndex < request.candidates().size()) {
          id = request.candidates().get(resultIndex).id();
        }
        if (verdict.equals(id)) verdict = "A compatible option based on color, style, and occasion.";
        String matchedId = id;
        String candidateLabel = request.candidates().stream()
            .filter(candidate -> candidate.id().equals(matchedId))
            .map(candidate -> candidate.label())
            .findFirst()
            .orElse("");
        int calibratedScore = calibrateOutfitScore(
            request.selectedLabel(),
            candidateLabel,
            Math.max(0, Math.min(100, rawScore)));
        if ("ollama".equals(providerName())) {
          int fashionRuleScore = fashionRuleScore(request.selectedLabel(), candidateLabel);
          calibratedScore = Math.round(calibratedScore * 0.3f + fashionRuleScore * 0.7f);
          verdict = fashionRuleVerdict(request.selectedLabel(), candidateLabel, calibratedScore);
        }
        byId.put(id, new OutfitBatchScore(
            id,
            calibratedScore,
            promptText(verdict)));
        resultIndex++;
      }
      List<OutfitBatchScore> ordered = request.candidates().stream()
          .map(candidate -> byId.get(candidate.id()))
          .filter(java.util.Objects::nonNull)
          .toList();
      if (ordered.isEmpty()) throw new IllegalStateException("AI returned no outfit scores.");
      return new OutfitBatchResponse(ordered);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("AI returned invalid outfit score JSON.");
    }
  }

  private <T> T parseJson(String text, Class<T> type, String provider) {
    try {
      return objectMapper.readValue(text, type);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException(provider + " returned invalid JSON.");
    }
  }

  private String providerError(RestClientResponseException error, String provider) {
    try {
      JsonNode body = objectMapper.readTree(error.getResponseBodyAsString());
      String message = body.path("error").path("message").asText(body.path("error").asText(""));
      if (!message.isBlank()) {
        return provider + " request failed: " + message;
      }
    } catch (JsonProcessingException ignored) {
      // Use HTTP status below.
    }
    return provider + " request failed with HTTP " + error.getStatusCode().value() + ".";
  }

  private String openAiOutputText(JsonNode result) {
    if (result == null) {
      return "";
    }
    JsonNode outputText = result.get("output_text");
    if (outputText != null && outputText.isTextual()) {
      return outputText.asText();
    }
    for (JsonNode item : result.path("output")) {
      for (JsonNode part : item.path("content")) {
        if ("output_text".equals(part.path("type").asText())) {
          return part.path("text").asText("");
        }
      }
    }
    return "";
  }

  private String extractJson(String text) {
    String trimmed = String.valueOf(text).trim()
        .replaceFirst("^```(?:json)?\\s*", "")
        .replaceFirst("\\s*```$", "")
        .trim();
    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
  }

  private ClothingAnalysis normalizeClothing(ClothingAnalysis analysis) {
    String name = promptText(analysis.name());
    String lowerName = name.toLowerCase();
    String requestedCategory = String.valueOf(analysis.category()).toLowerCase();
    String category = containsAny(lowerName, "dress", "jumpsuit", "romper") || "dress".equals(requestedCategory)
        ? "dress"
        : "bottom".equals(requestedCategory) ? "bottom" : "top";
    return new ClothingAnalysis(
        name,
        promptText(analysis.color()),
        category,
        promptText(analysis.pattern()),
        promptText(analysis.material()),
        promptText(analysis.occasion()),
        promptText(analysis.season()));
  }

  private OutfitScore normalizeScore(OutfitScore score) {
    return new OutfitScore(Math.max(0, Math.min(100, score.score())), promptText(score.verdict()));
  }

  private ClothingAnalysis fallbackClothingAnalysis(String response) {
    String lower = String.valueOf(response).toLowerCase();
    boolean bottom = lower.contains("jean") || lower.contains("pant") || lower.contains("trouser")
        || lower.contains("skirt") || lower.contains("short");
    return new ClothingAnalysis(
        bottom ? "Recognized bottom item" : "Recognized top item",
        inferColor(lower),
        bottom ? "bottom" : "top",
        "unknown",
        "unknown",
        "casual",
        "all season");
  }

  private String inferColor(String text) {
    for (String color : List.of("black", "white", "blue", "navy", "pink", "yellow", "green",
        "red", "brown", "gray", "beige", "cream", "purple", "orange")) {
      if (text.contains(color)) {
        return color;
      }
    }
    return "unknown";
  }

  private void ensureOpenAiConfigured() {
    if (!hasOpenAiKey()) {
      throw new IllegalStateException("OPENAI_API_KEY is missing in .env or environment variables.");
    }
  }

  private boolean hasOpenAiKey() {
    return openAiApiKey != null && !openAiApiKey.isBlank();
  }

  private String trendPromptLine(boolean includeTrendContext) {
    if (!includeTrendContext || fashionTrendContext == null || fashionTrendContext.isBlank()) {
      return "";
    }
    return "Also consider current fashion trends: " + promptText(fashionTrendContext);
  }

  private String promptText(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.length() > MAX_PROMPT_TEXT ? value.substring(0, MAX_PROMPT_TEXT) : value;
  }

  private String base64Payload(String dataUrl) {
    String value = String.valueOf(dataUrl);
    int comma = value.indexOf(',');
    return comma >= 0 ? value.substring(comma + 1).trim() : value.trim();
  }

  @SafeVarargs
  private Map<String, Object> userMessage(Map<String, Object>... content) {
    return Map.of("role", "user", "content", List.of(content));
  }

  private Map<String, Object> jsonSchemaFormat(String name, Map<String, Object> schema) {
    return Map.of("type", "json_schema", "name", name, "strict", true, "schema", schema);
  }

  private Map<String, Object> clothingAnalysisSchema() {
    return Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.ofEntries(
            Map.entry("name", Map.of("type", "string")),
            Map.entry("color", Map.of("type", "string")),
            Map.entry("category", Map.of("type", "string", "enum", List.of("top", "bottom", "dress"))),
            Map.entry("pattern", Map.of("type", "string")),
            Map.entry("material", Map.of("type", "string")),
            Map.entry("occasion", Map.of("type", "string")),
            Map.entry("season", Map.of("type", "string"))),
        "required", List.of("name", "color", "category", "pattern", "material", "occasion", "season"));
  }

  private Map<String, Object> outfitScoreSchema() {
    return Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
            "score", Map.of("type", "integer", "minimum", 0, "maximum", 100),
            "verdict", Map.of("type", "string")),
        "required", List.of("score", "verdict"));
  }

  private Map<String, Object> outfitBatchSchema() {
    Map<String, Object> result = Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
            "candidateId", Map.of("type", "string"),
            "score", Map.of("type", "integer", "minimum", 0, "maximum", 100),
            "verdict", Map.of("type", "string")),
        "required", List.of("candidateId", "score", "verdict"));
    return Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
            "results", Map.of("type", "array", "minItems", 1, "items", result)),
        "required", List.of("results"));
  }

  private Map<String, Object> shoppingQuerySchema() {
    return Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of("query", Map.of("type", "string")),
        "required", List.of("query"));
  }

  private int calibrateOutfitScore(String selectedLabel, String candidateLabel, int score) {
    String selected = String.valueOf(selectedLabel).toLowerCase();
    String candidate = String.valueOf(candidateLabel).toLowerCase();
    boolean selectedBottom = containsAny(selected, "category: bottom", "pants", "jeans", "trousers", "skirt", "shorts");
    boolean candidateBottom = containsAny(candidate, "category: bottom", "pants", "jeans", "trousers", "skirt", "shorts");
    boolean selectedOnePiece = containsAny(selected, "category: dress", "dress", "jumpsuit", "romper");
    boolean candidateOnePiece = containsAny(candidate, "category: dress", "dress", "jumpsuit", "romper");
    if ((selectedBottom && candidateOnePiece) || (candidateBottom && selectedOnePiece)) {
      return 0;
    }
    boolean selectedBrown = containsAny(selected, "brown", "chocolate", "cocoa");
    boolean candidateBrown = containsAny(candidate, "brown", "chocolate", "cocoa");
    boolean selectedRedOrange = containsAny(selected, "red", "orange", "rust", "terracotta");
    boolean candidateRedOrange = containsAny(candidate, "red", "orange", "rust", "terracotta");
    if ((selectedBrown && candidateRedOrange) || (candidateBrown && selectedRedOrange)) {
      score = Math.min(score, 74);
    }

    boolean selectedVolume = containsAny(selected, "puff", "peplum", "oversized", "voluminous");
    boolean candidateVolume = containsAny(candidate, "wide-leg", "wide leg", "baggy", "palazzo");
    if (selectedVolume && candidateVolume) {
      score = Math.min(score, 72);
    }
    return score;
  }

  private boolean containsAny(String text, String... terms) {
    for (String term : terms) {
      if (text.contains(term)) return true;
    }
    return false;
  }

  private String scoreBandVerdict(int score) {
    if (score >= 90) return "Exceptional pairing across color, silhouette, and occasion.";
    if (score >= 75) return "Strong pairing with coordinated color and proportions.";
    if (score >= 60) return "Wearable pairing with a minor styling compromise.";
    if (score >= 40) return "Weak pairing with noticeable color or silhouette tension.";
    return "This pairing has significant color, silhouette, or occasion conflicts.";
  }

  private int fashionRuleScore(String selectedLabel, String candidateLabel) {
    String selectedColor = labelField(selectedLabel, "color");
    String candidateColor = labelField(candidateLabel, "color");
    String selectedOccasion = labelField(selectedLabel, "occasion");
    String candidateOccasion = labelField(candidateLabel, "occasion");
    String selectedSeason = labelField(selectedLabel, "season");
    String candidateSeason = labelField(candidateLabel, "season");
    String selectedPattern = labelField(selectedLabel, "pattern");
    String candidatePattern = labelField(candidateLabel, "pattern");
    String selected = String.valueOf(selectedLabel).toLowerCase();
    String candidate = String.valueOf(candidateLabel).toLowerCase();

    int score = 50;
    if (selectedColor.equals(candidateColor) && !selectedColor.isBlank()) {
      score += 7;
    } else if (isNeutralColor(selectedColor) || isNeutralColor(candidateColor)) {
      score += 14;
    } else if (areComplementaryColors(selectedColor, candidateColor)) {
      score += 17;
    } else {
      score -= 4;
    }

    if (selectedOccasion.equals(candidateOccasion) && !selectedOccasion.isBlank()) {
      score += 12;
    } else if (occasionsWorkTogether(selectedOccasion, candidateOccasion)) {
      score += 6;
    } else {
      score -= 9;
    }

    if (selectedSeason.contains("all") || candidateSeason.contains("all")) {
      score += 7;
    } else if (!selectedSeason.isBlank() && (selectedSeason.contains(candidateSeason) || candidateSeason.contains(selectedSeason))) {
      score += 8;
    } else {
      score -= 5;
    }

    boolean selectedPatterned = !selectedPattern.isBlank() && !"solid".equals(selectedPattern) && !"unknown".equals(selectedPattern);
    boolean candidatePatterned = !candidatePattern.isBlank() && !"solid".equals(candidatePattern) && !"unknown".equals(candidatePattern);
    score += selectedPatterned && candidatePatterned ? -10 : 5;

    boolean selectedWide = containsAny(selected, "wide-leg", "wide leg", "baggy", "cargo", "palazzo");
    boolean candidateLoose = containsAny(candidate, "oversized", "tunic", "puff", "peplum", "ruffle");
    if (selectedWide && candidateLoose) score -= 9;
    if (selectedWide && containsAny(candidate, "fitted", "ribbed", "bodysuit", "tailored")) score += 8;

    return Math.max(10, Math.min(95, score));
  }

  private String fashionRuleVerdict(String selectedLabel, String candidateLabel, int score) {
    String selectedColor = labelField(selectedLabel, "color");
    String candidateColor = labelField(candidateLabel, "color");
    String selectedOccasion = labelField(selectedLabel, "occasion");
    String candidateOccasion = labelField(candidateLabel, "occasion");
    if (score >= 80) {
      return candidateColor + " creates strong color balance and the styling works for " + selectedOccasion + ".";
    }
    if (score >= 65) {
      return "The colors coordinate, with a small silhouette or occasion compromise.";
    }
    if (!occasionsWorkTogether(selectedOccasion, candidateOccasion)) {
      return "The " + candidateOccasion + " top does not fully match the " + selectedOccasion + " styling.";
    }
    return selectedColor + " and " + candidateColor + " need stronger contrast or cleaner proportions.";
  }

  private String labelField(String label, String field) {
    String prefix = field.toLowerCase() + ":";
    for (String part : String.valueOf(label).split(";")) {
      String value = part.trim();
      if (value.toLowerCase().startsWith(prefix)) {
        return value.substring(prefix.length()).trim().toLowerCase();
      }
    }
    return "";
  }

  private boolean isNeutralColor(String color) {
    return containsAny(color, "black", "white", "cream", "ivory", "beige", "taupe", "gray", "grey", "navy");
  }

  private boolean areComplementaryColors(String first, String second) {
    String pair = first + " " + second;
    return (containsAny(first, "brown", "tan", "camel") && containsAny(second, "blue", "cream", "white", "pink", "sage"))
        || (containsAny(second, "brown", "tan", "camel") && containsAny(first, "blue", "cream", "white", "pink", "sage"))
        || (containsAny(first, "green", "olive", "sage") && containsAny(second, "cream", "white", "yellow", "pink", "brown"))
        || (containsAny(second, "green", "olive", "sage") && containsAny(first, "cream", "white", "yellow", "pink", "brown"))
        || (pair.contains("red") && containsAny(pair, "cream", "white", "navy", "beige"))
        || (pair.contains("blue") && containsAny(pair, "white", "cream", "yellow", "coral", "brown"));
  }

  private boolean occasionsWorkTogether(String first, String second) {
    if (first.equals(second)) return true;
    String pair = first + " " + second;
    return (pair.contains("casual") && !containsAny(pair, "formal", "athletic", "beach"))
        || (pair.contains("work") && pair.contains("smart casual"))
        || (pair.contains("party") && pair.contains("formal"));
  }
}
