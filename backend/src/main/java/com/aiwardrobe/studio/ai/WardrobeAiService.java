package com.aiwardrobe.studio.ai;

import java.util.ArrayList;
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
    boolean visualBatch = isVisualBatchRequest(request);
    String prompt = visualBatch ? outfitVisualBatchPrompt(request) : outfitBatchPrompt(request);
    int maxOutputTokens = Math.min(4096, 160 + request.candidates().size() * 120);
    String response = switch (providerName()) {
      case "openai" -> scoreOutfitsWithOpenAi(request, prompt, visualBatch);
      case "ollama" -> callOllama(
          visualBatch ? ollamaModel : ollamaMatchModel,
          prompt,
          visualBatch ? visualBatchImages(request) : List.of(),
          outfitBatchSchema(),
          maxOutputTokens,
          visualBatch ? 8192 : 0);
      default -> throw new IllegalStateException("Unsupported AI_PROVIDER: " + aiProvider);
    };
    return parseBatchScores(response, request, visualBatch);
  }

  public String createShoppingQuery(String selectedItem, String targetCategory, String targetType) {
    String requiredGarment = switch (targetType) {
      case "any-top" -> "women's top, blouse, or shirt";
      case "any-bottom" -> "women's pants, skirt, or shorts";
      case "t-shirt" -> "women's t-shirt";
      default -> "women's " + targetType;
    };
    String prompt = """
        Act as a fashion stylist and create one focused Google Shopping query for the single best
        complementary garment to wear with the selected wardrobe item. Base the decision on garment
        type, exact color, silhouette/proportions, pattern, material, occasion, and season.
        Default to a polished, elegant, smart-casual-to-formal outfit that looks intentional and refined.
        Favor clean tailoring, fitted or softly draped shapes, fine knits, satin/silk, and crisp cotton or linen.
        Avoid clubwear, cutouts, sheer panels, distressed details, slogans, athletic pieces, and shapeless basics.
        The required shopping category is: %s.
        Choose exactly one primary color family, one silhouette, and at most one useful material.
        Do not return alternatives, color lists, slashes, commas, or the word "or".
        For a peplum, puff-sleeve, ruffled, oversized, or otherwise voluminous top, prefer a high-rise
        straight, tapered, slim, or tailored bottom that defines the waist. Avoid wide-leg, palazzo,
        baggy, balloon, heavily pleated, or equally voluminous bottoms.
        For beige, ivory, or cream tops, prefer deliberate contrast such as navy/deep indigo, olive,
        burgundy, chocolate, or black over a nearly identical beige unless monochrome is clearly best.
        For a red, crimson, burgundy, or wine skirt/bottom, default to an ivory, cream, white, black,
        or navy top. Do not choose a red-family or pink top unless the user explicitly requests monochrome.
        For a pleated, A-line, flared, full, midi, or maxi skirt, prefer a fitted, slim, ribbed, cropped,
        or bodysuit-style top that defines the waist. Avoid oversized, tunic, peplum, puff-sleeve,
        heavily ruffled, or longline tops that compete with the skirt's volume.
        If the selected item is patterned or textured, prefer a clean solid companion.
        Prefer wearable standalone garments over novelty, embellished, costume, or statement pieces.
        For casual spring or summer outfits, favor breathable fabrics and polished everyday shapes.
        Never recommend the same garment category as the selected item.
        Recommend one standalone garment only. Do not search for suits, matching sets,
        coordinated outfits, jumpsuits, dresses, jackets, cardigans, costumes, or multi-piece products.
        The query must be 5 to 10 shopping keywords, not a sentence, and must include the chosen color,
        silhouette, and required garment type. Do not repeat the selected garment's descriptive words
        unless they are necessary to describe the complementary item.
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
      query = applyShoppingContrastRules(query, selectedItem, targetCategory);
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

  String applyShoppingContrastRules(String query, String selectedItem, String targetCategory) {
    String selected = String.valueOf(selectedItem).toLowerCase();
    String adjusted = String.valueOf(query).trim();
    boolean selectedRedBottom = "top".equals(targetCategory)
        && containsAny(selected, "color: red", "color: crimson", "color: scarlet", "color: burgundy",
            "color: wine", " red ", " crimson ", " scarlet ", " burgundy ", " wine ")
        && containsAny(selected, "category: bottom", "skirt", "pants", "trousers", "shorts");
    if (!selectedRedBottom) return adjusted;

    adjusted = adjusted.replaceAll("(?i)\\b(red|crimson|scarlet|burgundy|wine|maroon|coral|pink)\\b", " ")
        .replaceAll("\\s+", " ").trim();
    if (!adjusted.matches("(?i).*\\b(ivory|cream|white|black|navy)\\b.*")) {
      adjusted = "ivory " + adjusted;
    }
    return adjusted;
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

    ClothingAnalysis analysis = normalizeClothing(parseJson(callOpenAi(body), ClothingAnalysis.class, "OpenAI"));
    try {
      Map<String, Object> verificationBody = Map.of(
          "model", openAiModel,
          "input", List.of(userMessage(
              Map.of("type", "input_text", "text", clothingVerificationPrompt(analysis)),
              Map.of("type", "input_image", "image_url", imageDataUrl))),
          "text", Map.of("format", jsonSchemaFormat("clothing_analysis_verification", clothingAnalysisSchema())));
      ClothingAnalysis verification = parseJson(callOpenAi(verificationBody), ClothingAnalysis.class, "OpenAI");
      return normalizeClothing(verification);
    } catch (IllegalStateException error) {
      return analysis;
    }
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

  private String scoreOutfitsWithOpenAi(OutfitBatchRequest request, String prompt, boolean visualBatch) {
    ensureOpenAiConfigured();
    List<Map<String, Object>> content = new ArrayList<>();
    content.add(Map.of("type", "input_text", "text", prompt));
    if (visualBatch) {
      content.add(Map.of("type", "input_text", "text", "Selected item image:"));
      content.add(Map.of("type", "input_image", "image_url", request.selectedImage()));
      request.candidates().forEach(candidate -> {
        content.add(Map.of("type", "input_text", "text", "Candidate ID " + promptText(candidate.id()) + " image:"));
        content.add(Map.of("type", "input_image", "image_url", candidate.image()));
      });
    }
    Map<String, Object> body = Map.of(
        "model", openAiModel,
        "input", List.of(Map.of("role", "user", "content", content)),
        "text", Map.of("format", jsonSchemaFormat("outfit_scores", outfitBatchSchema())));
    return callOpenAi(body);
  }

  private ClothingAnalysis analyzeClothingWithOllama(String imageDataUrl) {
    String response = callOllama(
        ollamaModel,
        clothingAnalysisPrompt(),
        List.of(base64Payload(imageDataUrl)),
        clothingAnalysisSchema());

    ClothingAnalysis analysis;
    try {
      analysis = normalizeClothing(parseJson(extractJson(response), ClothingAnalysis.class, "Ollama"));
    } catch (IllegalStateException error) {
      return fallbackClothingAnalysis(response);
    }
    try {
      String verificationResponse = callOllama(
          ollamaModel,
          clothingVerificationPrompt(analysis),
          List.of(base64Payload(imageDataUrl)),
          clothingAnalysisSchema());
      ClothingAnalysis verification = parseJson(extractJson(verificationResponse), ClothingAnalysis.class, "Ollama");
      return normalizeClothing(verification);
    } catch (IllegalStateException error) {
      return analysis;
    }
  }

  private OutfitScore scoreOutfitWithOllama(OutfitScoreRequest request) {
    String response = callOllama(
        ollamaModel,
        outfitScorePrompt(request, false),
        List.of(base64Payload(request.selectedImage()), base64Payload(request.candidateImage())),
        outfitScoreSchema(),
        220);
    OutfitScore result = normalizeScore(parseJson(extractJson(response), OutfitScore.class, "Ollama"));
    int score = calibrateOutfitScore(request.selectedLabel(), request.candidateLabel(), result.score());
    return new OutfitScore(score, compactVerdict(result.verdict()));
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
    return callOllama(model, prompt, images, schema, maxOutputTokens, 0);
  }

  private String callOllama(
      String model,
      String prompt,
      List<String> images,
      Map<String, Object> schema,
      int maxOutputTokens,
      int contextTokens) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("prompt", prompt);
    body.put("stream", false);
    body.put("format", schema);
    Map<String, Object> options = new LinkedHashMap<>();
    options.put("temperature", 0.1);
    options.put("num_predict", maxOutputTokens);
    if (contextTokens > 0) options.put("num_ctx", contextTokens);
    body.put("options", options);
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
        Identify the single garment being showcased in this image. When a model wears several garments,
        choose the featured garment that is centered, most visually emphasized, and presented for sale;
        treat other garments as supporting styling and ignore them. Also ignore accessories, shoes, bags,
        background, and body parts.
        First locate the featured garment's neckline/waist/hem boundaries. Then classify only that garment.
        Return compact JSON matching the schema:
        name: specific product-style name with visible length/rise, silhouette/fit, and garment type.
        Build the name only from details visible on the featured garment. Never copy wording from these
        instructions and never mix the name, color, material, or construction of two different garments.
        color: specific color name
        category: top, bottom, or dress
        Classify from the garment's visible construction, not the person wearing it or the generated name.
        Use top for blouses, shirts, tees, sweaters, jackets, and waist/hip-length peplum garments.
        Use bottom for pants, trousers, jeans, skirts, and shorts, even when a matching top is visible.
        A skirt begins at the waist and leaves the torso covered by a separate top. Long, midi, maxi,
        pleated, and full-length skirts are always bottoms, never dresses.
        Use dress only when one continuous garment visibly covers both the torso and lower body,
        including genuine dresses, jumpsuits, rompers, and other one-piece outfits.
        Puff sleeves, a fitted waist, peplum fabric, or a model wearing pants do not make a top a dress.
        Before returning JSON, verify all seven fields describe the same featured garment. The garment
        word in name must agree with category, and material must describe that garment rather than jeans,
        pants, or another supporting piece elsewhere in the image.
        pattern: solid, striped, floral, plaid, checked, polka dot, graphic, lace, ribbed, or unknown
        material: cotton, linen, denim, knit, ribbed knit, chiffon, satin, leather, wool, polyester, or unknown
        occasion: casual, smart casual, work, formal, party, lounge, athletic, or beach
        season: spring, summer, fall, winter, spring/summer, fall/winter, or all season
        """;
  }

  private String clothingVerificationPrompt(ClothingAnalysis firstPass) {
    return """
        Independently audit this clothing result against the image. The previous result is untrusted and
        may have combined the title of one garment with the color or material of another.
        Identify the single featured garment: the centered, visually emphasized item being showcased.
        Ignore supporting garments worn only to style it, plus accessories, bags, and background.
        Trace the featured garment from neckline or waist to its hem before deciding its category.
        A dress must visibly continue from the torso below the hips as one garment and cover the upper legs.
        A garment ending at the waist or hips is a top, even if it has puff sleeves, a fitted waist, gathers,
        or a flared peplum hem. A model's jeans do not make a featured blouse a bottom or denim item.
        Pants, jeans, trousers, skirts, and shorts are bottoms. A skirt has its own waistband and does not
        cover the torso; its length does not make it a dress. A separate crop top plus a skirt is not one-piece.
        Return the full clothing JSON again using only visible traits of that one garment. Correct every
        conflicting field. Name, color, category, pattern, and material must all refer to the same item.
        Previous result: %s
        """.formatted(promptText(firstPass.toString()));
  }

  private String outfitScorePrompt(OutfitScoreRequest request, boolean includeTrendContext) {
    return """
        Act as a critical fashion stylist. Image 1 is the selected garment and image 2 is the candidate.
        Inspect the actual images at match time; use the text labels only as supporting metadata.
        Score how well they create a polished, elegant, smart-casual-to-formal outfit from 0 to 100.
        Weight silhouette/proportion 30%%, occasion and refinement 25%%, color/pattern 20%%,
        material/season 15%%, and current styling relevance 10%%.
        A neutral color or shared casual occasion is only a baseline, not proof of a strong match.
        Reward clean tailoring, refined blouses, fitted fine knits, satin/silk, crisp cotton/linen,
        controlled drape, and deliberate waist definition. Penalize cutouts, sheer panels, distressed
        details, graphic slogans, clubwear, athletic pieces, and overly casual crop/tube tops unless the
        selected garment clearly calls for that direction.
        For peplum, puff-sleeve, ruffled, or voluminous tops, reward waist-defining straight, tapered,
        slim, pencil, or tailored bottoms and penalize wide, flared, heavily pleated, or bulky bottoms.
        If both pieces are patterned or highly textured, penalize visual competition unless clearly intentional.
        %s
        Return compact JSON with score and a specific one-sentence verdict under 120 characters.
        Name the decisive strength or weakness; never use generic wording such as "strong color balance."
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
        Act as a critical fashion stylist. Compare every candidate against the selected clothing item,
        then score each outfit from 0 to 100. Rank candidates relative to one another, not independently.
        The default goal is polished, elegant, smart-casual-to-formal styling—not merely casual compatibility.
        Weight silhouette/proportion 30%%, occasion and refinement 25%%, color/pattern 20%%,
        material/season 15%%, and current styling relevance 10%%.
        First verify garment compatibility. A dress, jumpsuit, romper, or other one-piece outfit
        must never be matched as a top with pants, jeans, shorts, or a skirt.
        Judge the actual named garment type, not just its color.
        A neutral color or shared casual occasion is only a baseline and cannot by itself score above 74.
        Reward tailored construction, refined blouses, fine knits, satin/silk, crisp cotton/linen,
        controlled drape, and clean waist definition. Penalize cutouts, sheer/mesh panels, distressed
        details, graphic slogans, clubwear, athletic pieces, tube tops, and very casual cropped tanks.
        For peplum, puff-sleeve, ruffled, or voluminous tops, reward high-rise straight, tapered, slim,
        pencil, or tailored bottoms that define the waist. Penalize wide, flared, heavily pleated,
        baggy, or equally voluminous bottoms that add bulk at the waist or hips.
        If the selected item is patterned or textured, prefer a solid candidate; penalize competing patterns.
        Use this strict scale:
        90-100: exceptional, editorial-level pairing with no meaningful conflict.
        75-89: strong and clearly coordinated.
        60-74: wearable, but has a noticeable color, silhouette, or formality issue.
        40-59: weak pairing with multiple conflicts.
        0-39: clear clash.
        Reserve 90+ for a clearly exceptional match across every weighted category.
        Avoid tied scores unless two candidates are genuinely indistinguishable.
        Penalize near-but-not-matching warm colors such as brown with bright red/orange.
        Penalize competing volume, such as a voluminous puff/peplum top with very wide bottoms.
        %s
        Return exactly one result for each candidate ID. Keep each verdict under 120 characters.
        Every verdict must mention that candidate's decisive strength or weakness and must not repeat
        a generic template such as "creates strong color balance" across candidates.
        Selected item: %s
        Candidates:%s
        """.formatted(
        trendPromptLine(true),
        promptText(request.selectedLabel()),
        candidates);
  }

  private String outfitVisualBatchPrompt(OutfitBatchRequest request) {
    StringBuilder candidates = new StringBuilder();
    StringBuilder imageOrder = new StringBuilder("Image 1 is the selected item");
    for (int index = 0; index < request.candidates().size(); index++) {
      var candidate = request.candidates().get(index);
      candidates.append("\n- ID ").append(promptText(candidate.id()))
          .append(": ").append(promptText(candidate.label()));
      imageOrder.append("; image ").append(index + 2)
          .append(" is candidate ID ").append(promptText(candidate.id()));
    }
    return """
        Act as a critical fashion stylist performing the final visual comparison of one selected
        clothing item with six or fewer candidate items. Inspect the actual images for exact color,
        silhouette, proportions, pattern, texture, construction, and formality. Use the descriptions
        only as supporting hints when a visual detail is unclear.
        Rank all candidates relative to each other. Weight silhouette/proportion 30%%, occasion and
        refinement 25%%, color/pattern 20%%, material/season 15%%, and styling relevance 10%%.
        Reject incompatible garment combinations. Penalize competing volume, competing patterns,
        clashing undertones, and mismatched formality. Reward intentional contrast, waist definition,
        balanced proportions, and polished coordination. Reserve 90+ for exceptional pairings.
        Return exactly one result for every candidate ID, preserving each ID exactly. Each verdict
        must name a visible decisive strength or weakness in under 120 characters.
        Image order: %s.
        Selected item description: %s
        Candidate descriptions:%s
        """.formatted(
        imageOrder,
        promptText(request.selectedLabel()),
        candidates);
  }

  private boolean isVisualBatchRequest(OutfitBatchRequest request) {
    boolean hasSelectedImage = request.selectedImage() != null && !request.selectedImage().isBlank();
    boolean hasAnyCandidateImage = request.candidates().stream()
        .anyMatch(candidate -> candidate.image() != null && !candidate.image().isBlank());
    if (!hasSelectedImage && !hasAnyCandidateImage) return false;
    boolean hasEveryCandidateImage = request.candidates().stream()
        .allMatch(candidate -> candidate.image() != null && !candidate.image().isBlank());
    if (!hasSelectedImage || !hasEveryCandidateImage) {
      throw new IllegalArgumentException("Visual outfit scoring requires the selected image and every candidate image.");
    }
    if (request.candidates().size() > 6) {
      throw new IllegalArgumentException("Visual outfit scoring supports at most 6 candidates.");
    }
    return true;
  }

  private List<String> visualBatchImages(OutfitBatchRequest request) {
    List<String> images = new ArrayList<>();
    images.add(base64Payload(request.selectedImage()));
    request.candidates().forEach(candidate -> images.add(base64Payload(candidate.image())));
    return images;
  }

  private OutfitBatchResponse parseBatchScores(String text, OutfitBatchRequest request, boolean visualBatch) {
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
        if ("ollama".equals(providerName()) && !visualBatch) {
          int fashionRuleScore = fashionRuleScore(request.selectedLabel(), candidateLabel);
          calibratedScore = Math.round(calibratedScore * 0.3f + fashionRuleScore * 0.7f);
          calibratedScore = calibrateOutfitScore(request.selectedLabel(), candidateLabel, calibratedScore);
          verdict = fashionRuleVerdict(request.selectedLabel(), candidateLabel, calibratedScore);
        }
        byId.put(id, new OutfitBatchScore(
            id,
            calibratedScore,
            compactVerdict(verdict)));
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

  ClothingAnalysis normalizeClothing(ClothingAnalysis analysis) {
    String name = promptText(analysis.name());
    String lowerName = name.toLowerCase();
    String requestedCategory = String.valueOf(analysis.category()).trim().toLowerCase();
    boolean namedBottom = containsAny(lowerName, "pants", "trousers", "jeans", "skirt", "shorts", "palazzo", "culottes");
    boolean namedTop = containsAny(lowerName, "blouse", "shirt", "t-shirt", "tee", "sweater", "hoodie", "top", "peplum");
    boolean namedOnePiece = containsAny(lowerName, "dress", "jumpsuit", "romper", "one-piece", "one piece");
    String namedCategory = namedBottom ? "bottom" : namedTop ? "top" : namedOnePiece ? "dress" : "";
    String category;
    if (!namedCategory.isBlank()) {
      category = namedCategory;
    } else if (List.of("top", "bottom", "dress").contains(requestedCategory)) {
      category = requestedCategory;
    } else {
      category = "top";
    }
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
    return new OutfitScore(Math.max(0, Math.min(100, score.score())), compactVerdict(score.verdict()));
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
    if (!includeTrendContext) return "";
    if (fashionTrendContext == null || fashionTrendContext.isBlank()) {
      return "Current direction: polished minimalism, refined separates, intentional contrast, and balanced volume; full midi skirts work best with simpler fitted tops.";
    }
    return "Also consider current fashion trends: " + promptText(fashionTrendContext);
  }

  private String promptText(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.length() > MAX_PROMPT_TEXT ? value.substring(0, MAX_PROMPT_TEXT) : value;
  }

  private String compactVerdict(String value) {
    String verdict = promptText(value);
    return verdict.length() <= 120 ? verdict : verdict.substring(0, 117).stripTrailing() + "...";
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

    boolean selectedRefined = containsAny(selected, "skirt", "dress", "tailored", "pleated", "satin", "silk", "formal", "work");
    boolean candidatePolished = containsAny(candidate, "blouse", "tailored", "fitted", "structured", "fine knit",
        "fine-knit", "satin", "silk", "crepe", "button-down", "button down", "pencil", "straight");
    boolean candidateOverlyCasual = containsAny(candidate, "cutout", "cut-out", "mesh", "sheer", "distressed",
        "graphic", "tube top", "cropped tank", "crop tank", "club", "athletic", "lounge", "sweatshirt");
    if (candidatePolished) score = Math.min(95, score + 7);
    if (candidateOverlyCasual) score = Math.min(score, selectedRefined ? 66 : 72);

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

  int fashionRuleScore(String selectedLabel, String candidateLabel) {
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
      score += containsAny(selectedColor, "red", "crimson", "scarlet", "burgundy", "wine") ? -3 : 6;
    } else if (areComplementaryColors(selectedColor, candidateColor)) {
      score += 17;
    } else if (isNeutralColor(selectedColor) && isNeutralColor(candidateColor)) {
      score += 9;
    } else if (isNeutralColor(selectedColor) || isNeutralColor(candidateColor)) {
      score += 12;
    } else {
      score -= 4;
    }

    boolean candidatePolishedOccasion = containsAny(candidateOccasion, "smart casual", "work", "formal");
    if (selectedOccasion.equals(candidateOccasion) && !selectedOccasion.isBlank()) {
      score += 12;
    } else if (occasionsWorkTogether(selectedOccasion, candidateOccasion)) {
      score += 6;
    } else if ("casual".equals(selectedOccasion) && candidatePolishedOccasion) {
      score += 5;
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

    boolean selectedTopVolume = containsAny(selected, "peplum", "puff", "ruffle", "ruffled", "oversized", "voluminous", "gathered");
    boolean candidateBottomVolume = containsAny(candidate, "wide-leg", "wide leg", "palazzo", "baggy", "balloon", "flared", "pleated", "tiered", "a-line");
    boolean candidateWaistDefining = containsAny(candidate, "high-rise", "high rise", "high-waist", "high waist", "straight", "tapered", "slim", "pencil", "tailored", "cigarette");
    if (selectedTopVolume && candidateWaistDefining) score += 10;
    if (selectedTopVolume && candidateBottomVolume) score -= 12;
    if (selectedTopVolume && containsAny(candidate, "shorts") && !candidateWaistDefining) score -= 5;
    if (selectedTopVolume && containsAny(candidate, "skirt") && !candidateWaistDefining && !candidateBottomVolume) score -= 3;

    boolean selectedWide = containsAny(selected, "wide-leg", "wide leg", "baggy", "cargo", "palazzo");
    boolean candidateLoose = containsAny(candidate, "oversized", "tunic", "puff", "peplum", "ruffle");
    if (selectedWide && candidateLoose) score -= 9;
    if (selectedWide && containsAny(candidate, "fitted", "ribbed", "bodysuit", "tailored")) score += 8;

    boolean candidatePolished = containsAny(candidate, "blouse", "tailored", "fitted", "structured", "fine knit",
        "fine-knit", "satin", "silk", "crepe", "button-down", "button down", "pencil", "straight");
    boolean candidateOverlyCasual = containsAny(candidate, "cutout", "cut-out", "mesh", "sheer", "distressed",
        "graphic", "tube top", "cropped tank", "crop tank", "club", "athletic", "lounge", "sweatshirt");
    if (candidatePolished) score += 9;
    if (candidateOverlyCasual) score -= 18;

    return Math.max(10, Math.min(95, score));
  }

  String fashionRuleVerdict(String selectedLabel, String candidateLabel, int score) {
    String selectedColor = labelField(selectedLabel, "color");
    String candidateColor = labelField(candidateLabel, "color");
    String selectedOccasion = labelField(selectedLabel, "occasion");
    String candidateOccasion = labelField(candidateLabel, "occasion");
    String selectedPattern = labelField(selectedLabel, "pattern");
    String candidatePattern = labelField(candidateLabel, "pattern");
    String selected = String.valueOf(selectedLabel).toLowerCase();
    String candidate = String.valueOf(candidateLabel).toLowerCase();
    String candidateName = String.valueOf(candidateLabel).split(";", 2)[0].trim();
    boolean selectedTopVolume = containsAny(selected, "peplum", "puff", "ruffle", "ruffled", "oversized", "voluminous", "gathered");
    boolean candidateBottomVolume = containsAny(candidate, "wide-leg", "wide leg", "palazzo", "baggy", "balloon", "flared", "pleated", "tiered", "a-line");
    boolean candidateWaistDefining = containsAny(candidate, "high-rise", "high rise", "high-waist", "high waist", "straight", "tapered", "slim", "pencil", "tailored", "cigarette");
    boolean bothPatterned = !List.of("", "solid", "unknown").contains(selectedPattern)
        && !List.of("", "solid", "unknown").contains(candidatePattern);
    boolean candidatePolished = containsAny(candidate, "blouse", "tailored", "fitted", "structured", "fine knit",
        "fine-knit", "satin", "silk", "crepe", "button-down", "button down", "pencil", "straight");
    boolean candidateOverlyCasual = containsAny(candidate, "cutout", "cut-out", "mesh", "sheer", "distressed",
        "graphic", "tube top", "cropped tank", "crop tank", "club", "athletic", "lounge", "sweatshirt");

    if (selectedTopVolume && candidateBottomVolume) {
      return candidateName + " adds volume at the waist; a straighter bottom would balance this top better.";
    }
    if (selectedTopVolume && candidateWaistDefining) {
      return candidateName + " defines the waist and balances the top's volume cleanly.";
    }
    if (bothPatterned) {
      return candidateName + " competes with the top's texture; a solid bottom would look more intentional.";
    }
    if (candidateOverlyCasual) {
      return candidateName + " reads too casual; a cleaner tailored piece would look more polished.";
    }
    if (candidatePolished && score >= 75) {
      return candidateName + " adds refined structure and creates a polished, intentional outfit.";
    }
    if (selectedTopVolume && containsAny(candidate, "shorts", "skirt")) {
      return candidateName + " works in color, but its proportions are less balanced than a straight high-rise bottom.";
    }
    if (score >= 80) {
      return candidateName + " gives the " + selectedColor + " piece clean contrast and suits " + selectedOccasion + " styling.";
    }
    if (score >= 65) {
      return candidateName + " coordinates in color, with a small silhouette or occasion compromise.";
    }
    if (!occasionsWorkTogether(selectedOccasion, candidateOccasion)) {
      return candidateName + " feels too " + candidateOccasion + " for the selected item's " + selectedOccasion + " styling.";
    }
    return candidateName + " needs stronger contrast or cleaner proportions with this " + selectedColor + " piece.";
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
    return (containsAny(first, "beige", "ivory", "cream", "ecru") && containsAny(second, "navy", "blue", "indigo", "denim", "olive", "sage", "burgundy", "wine", "chocolate", "brown", "black"))
        || (containsAny(second, "beige", "ivory", "cream", "ecru") && containsAny(first, "navy", "blue", "indigo", "denim", "olive", "sage", "burgundy", "wine", "chocolate", "brown", "black"))
        || (containsAny(first, "brown", "tan", "camel") && containsAny(second, "blue", "cream", "white", "pink", "sage"))
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
