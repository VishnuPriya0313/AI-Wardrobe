package com.aiwardrobe.studio.shopping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.aiwardrobe.studio.api.dto.ShoppingOption;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ShoppingSearchService {

  private static final int SHOPPING_OPTION_LIMIT = 5;

  private final String apiKey;
  private final RestClient client;

  public ShoppingSearchService(@Value("${shopping.serpapi.api-key:}") String apiKey) {
    this.apiKey = apiKey;
    this.client = RestClient.builder().baseUrl("https://serpapi.com").build();
  }

  public List<ShoppingOption> search(String query, String targetCategory, String targetType) {
    return search(query, targetCategory, targetType, "");
  }

  public List<ShoppingOption> search(String query, String targetCategory, String targetType, String selectedItem) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("Online shopping is not configured. Add SERPAPI_API_KEY to the backend environment.");
    }

    JsonNode response;
    try {
      response = client.get()
          .uri(uri -> uri.path("/search.json")
              .queryParam("engine", "google_shopping")
              .queryParam("q", query)
              .queryParam("api_key", apiKey)
              .queryParam("hl", "en")
              .queryParam("gl", "us")
              .build())
          .retrieve()
          .body(JsonNode.class);
    } catch (Exception error) {
      throw new IllegalStateException("Could not search online stores right now.");
    }

    if (response == null) return List.of();

    List<ScoredShoppingOption> strictOptions = new ArrayList<>();
    List<ScoredShoppingOption> relaxedOptions = new ArrayList<>();
    for (JsonNode product : response.path("shopping_results")) {
      String title = product.path("title").asText("Online option");
      String url = product.path("product_link").asText(product.path("link").asText(""));
      if (url.isBlank() || !isStandaloneGarment(title) || !matchesTargetCategory(title, targetCategory)) continue;

      ShoppingOption option = new ShoppingOption(
          title,
          product.path("price").asText(""),
          product.path("source").asText("Online store"),
          product.path("thumbnail").asText(""),
          url);
      int score = styleScore(title, targetCategory, targetType, selectedItem);
      if (matchesTargetType(title, targetType)) {
        addUniqueScoredOption(strictOptions, new ScoredShoppingOption(option, score));
      } else {
        addUniqueScoredOption(relaxedOptions, new ScoredShoppingOption(option, score - 20));
      }
    }

    List<ShoppingOption> options = new ArrayList<>();
    strictOptions.stream()
        .sorted(Comparator.comparingInt(ScoredShoppingOption::score).reversed())
        .map(ScoredShoppingOption::option)
        .forEach(option -> {
          if (options.size() < SHOPPING_OPTION_LIMIT) addUniqueOption(options, option);
        });
    relaxedOptions.stream()
        .sorted(Comparator.comparingInt(ScoredShoppingOption::score).reversed())
        .map(ScoredShoppingOption::option)
        .forEach(option -> {
          if (options.size() < SHOPPING_OPTION_LIMIT) addUniqueOption(options, option);
        });
    return options;
  }

  private boolean matchesTargetCategory(String title, String targetCategory) {
    String value = String.valueOf(title).toLowerCase();
    if ("top".equals(targetCategory)) {
      return !containsWord(value, "pant", "pants", "trouser", "trousers", "jean", "jeans",
          "skirt", "shorts", "palazzo", "dress", "jumpsuit");
    }
    return !containsWord(value, "blouse", "shirt", "tee", "tshirt", "t-shirt", "sweater",
        "sweatshirt", "bodysuit", "dress", "jumpsuit", "jacket", "cardigan", "coat", "blazer", "vest");
  }

  private boolean matchesTargetType(String title, String targetType) {
    String value = String.valueOf(title).toLowerCase();
    return switch (targetType) {
      case "any-top", "any-bottom" -> true;
      case "pants" -> containsWord(value, "pant", "pants", "trouser", "trousers", "jean", "jeans", "palazzo")
          && !containsWord(value, "jacket", "cardigan", "coat", "blazer");
      case "skirt" -> containsWord(value, "skirt", "skirts");
      case "shorts" -> containsWord(value, "short", "shorts")
          && !containsWord(value, "jacket", "cardigan", "coat", "blazer");
      case "blouse" -> containsWord(value, "blouse", "blouses", "top");
      case "shirt" -> containsWord(value, "shirt", "shirts", "button-down", "button down")
          && !containsWord(value, "t-shirt", "tee", "tshirt");
      case "t-shirt" -> containsWord(value, "t-shirt", "tshirt", "tee", "tees");
      default -> false;
    };
  }

  private boolean isStandaloneGarment(String title) {
    String value = String.valueOf(title).toLowerCase();
    return !containsWord(value,
        "suit", "set", "sets", "matching set", "outfit set", "two-piece", "two piece",
        "2-piece", "2pcs", "2 pcs", "3-piece", "three-piece", "co-ord", "coord set",
        "jumpsuit", "romper", "costume");
  }

  private int styleScore(String title, String targetCategory, String targetType, String selectedItem) {
    String value = String.valueOf(title).toLowerCase();
    String selected = String.valueOf(selectedItem).toLowerCase();
    int score = 50;

    if (matchesTargetType(value, targetType)) score += 12;
    if ("bottom".equals(targetCategory)) {
      if (containsWord(value, "high-rise", "high rise", "mid-rise", "mid rise", "straight",
          "tailored", "linen", "cotton", "chino")) score += 8;
      if (containsWord(value, "cargo", "distressed", "ripped", "embellished", "embroidered", "applique")) score -= 16;
    }
    if ("top".equals(targetCategory)) {
      if (containsWord(value, "linen", "cotton", "button-down", "button down", "relaxed", "tailored")) score += 8;
      if (containsWord(value, "embellished", "costume", "sequined")) score -= 16;
    }

    if (containsAny(selected, "color: blue", " blue")) {
      if (containsWord(value, "white", "ivory", "cream", "beige", "khaki", "tan", "camel", "stone", "ecru")) score += 18;
      if (containsWord(value, "light wash", "medium wash", "blue", "navy", "denim")) score += 5;
      if (containsWord(value, "black", "red", "orange", "purple")) score -= 6;
    }
    if (containsAny(selected, "pattern: ribbed", " ribbed")) {
      if (containsWord(value, "solid", "plain", "linen", "cotton", "tailored")) score += 8;
      if (containsWord(value, "embroidered", "floral", "graphic", "print", "printed")) score -= 10;
    }
    if (containsAny(selected, "occasion: casual", " casual")) {
      if (containsWord(value, "casual", "everyday", "relaxed", "chino", "linen", "denim")) score += 8;
      if (containsWord(value, "formal", "evening", "club", "party")) score -= 12;
    }
    if (containsAny(selected, "season: spring", " spring")) {
      if (containsWord(value, "spring", "summer", "linen", "cotton", "lightweight")) score += 7;
      if (containsWord(value, "winter", "fleece", "wool", "thermal")) score -= 10;
    }

    return score;
  }

  private void addUniqueOption(List<ShoppingOption> options, ShoppingOption option) {
    boolean alreadyAdded = options.stream().anyMatch(existing -> existing.url().equals(option.url()));
    if (!alreadyAdded) options.add(option);
  }

  private void addUniqueScoredOption(List<ScoredShoppingOption> options, ScoredShoppingOption option) {
    boolean alreadyAdded = options.stream().anyMatch(existing -> existing.option().url().equals(option.option().url()));
    if (!alreadyAdded) options.add(option);
  }

  private boolean containsAny(String text, String... terms) {
    for (String term : terms) {
      if (text.contains(term)) return true;
    }
    return false;
  }

  private boolean containsWord(String text, String... terms) {
    for (String term : terms) {
      String pattern = "(^|[^a-z0-9])" + Pattern.quote(term) + "([^a-z0-9]|$)";
      if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find()) return true;
    }
    return false;
  }

  private record ScoredShoppingOption(ShoppingOption option, int score) {
  }
}
