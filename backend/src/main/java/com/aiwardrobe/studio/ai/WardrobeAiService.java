package com.aiwardrobe.studio.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aiwardrobe.studio.api.dto.ClothingAnalysis;
import com.aiwardrobe.studio.api.dto.OutfitScore;
import com.aiwardrobe.studio.api.dto.OutfitScoreRequest;
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
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("prompt", prompt);
    body.put("stream", false);
    body.put("format", schema);
    body.put("options", Map.of("temperature", 0.1, "num_predict", 220));
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
        category: top or bottom
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
    String category = "bottom".equalsIgnoreCase(String.valueOf(analysis.category())) ? "bottom" : "top";
    return new ClothingAnalysis(
        promptText(analysis.name()),
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
            Map.entry("category", Map.of("type", "string", "enum", List.of("top", "bottom"))),
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
}
