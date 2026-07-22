package com.aiwardrobe.studio.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShoppingSearchServiceTest {

  private final ShoppingSearchService service = new ShoppingSearchService("");

  @Test
  void prioritizesCleanWaistDefiningPantsForPeplumTop() {
    String selected = "beige peplum blouse; category: top; color: beige; pattern: lace; "
        + "material: cotton; occasion: casual; season: spring/summer";
    String query = "women's pants navy high-rise straight tailored cotton";

    int straightScore = service.styleScore(
        "Women's High-Rise Straight Tailored Cotton Pants",
        "bottom", "pants", selected, query);
    int wideScore = service.styleScore(
        "Women's Easy Wide-Leg Linen Blend Pants",
        "bottom", "pants", selected, query);
    int palazzoScore = service.styleScore(
        "Women's Beige Loose Palazzo Linen Pants",
        "bottom", "pants", selected, query);

    assertThat(straightScore).isGreaterThan(wideScore);
    assertThat(straightScore).isGreaterThan(palazzoScore);
  }

  @Test
  void prioritizesContrastingFittedTopForFullRedSkirt() {
    String selected = "red pleated midi skirt; category: bottom; color: red; pattern: solid; "
        + "material: polyester; occasion: casual; season: all season";
    String query = "women's top ivory fitted ribbed cotton";

    int ivoryFittedScore = service.styleScore(
        "Women's Ivory Fitted Ribbed Cotton Top", "top", "any-top", selected, query);
    int burgundyPeplumScore = service.styleScore(
        "Women's Burgundy Loose Peplum Blouse", "top", "any-top", selected, query);

    assertThat(ivoryFittedScore).isGreaterThan(burgundyPeplumScore);
  }
}
