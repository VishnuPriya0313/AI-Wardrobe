package com.aiwardrobe.studio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiwardrobe.studio.api.dto.ClothingAnalysis;
import com.aiwardrobe.studio.api.dto.OutfitBatchRequest;
import com.aiwardrobe.studio.api.dto.OutfitCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WardrobeAiServiceTest {

  private final WardrobeAiService service = new WardrobeAiService(
      new ObjectMapper(), "ollama", "", "unused", "http://127.0.0.1:11434",
      "unused", "unused", "");

  @Test
  void ranksSolidDenimAboveCompetingPlaidForTexturedPeplumTop() {
    String selected = "beige peplum blouse with fitted waist; category: top; color: beige; "
        + "pattern: lace; material: cotton; occasion: casual; season: spring/summer";
    String denimSkirt = "denim skirt; category: bottom; color: blue; pattern: solid; "
        + "material: denim; occasion: casual; season: all season";
    String plaidSkirt = "plaid skirt; category: bottom; color: brown; pattern: plaid; "
        + "material: woven fabric; occasion: casual; season: fall";

    assertThat(service.fashionRuleScore(selected, denimSkirt))
        .isGreaterThan(service.fashionRuleScore(selected, plaidSkirt));
    assertThat(service.fashionRuleVerdict(selected, plaidSkirt, 68))
        .contains("competes");
  }

  @Test
  void rewardsStraightBottomAndPenalizesCompetingVolume() {
    String selected = "beige peplum blouse; category: top; color: beige; pattern: solid; "
        + "material: cotton; occasion: smart casual; season: spring/summer";
    String straightPants = "navy high-rise straight tailored pants; category: bottom; color: navy; "
        + "pattern: solid; material: cotton; occasion: smart casual; season: spring/summer";
    String palazzoPants = "beige wide-leg palazzo pants; category: bottom; color: beige; "
        + "pattern: solid; material: linen; occasion: casual; season: spring/summer";

    assertThat(service.fashionRuleScore(selected, straightPants))
        .isGreaterThan(service.fashionRuleScore(selected, palazzoPants));
    assertThat(service.fashionRuleVerdict(selected, palazzoPants, 70))
        .contains("volume");
  }

  @Test
  void usesSpecificGarmentNameWhenCategoryFieldConflicts() {
    ClothingAnalysis inconsistent = new ClothingAnalysis(
        "red pleated midi skirt", "red", "dress", "solid", "polyester",
        "casual", "spring/summer");

    ClothingAnalysis normalized = service.normalizeClothing(inconsistent);

    assertThat(normalized.category()).isEqualTo("bottom");
    assertThat(normalized.name()).isEqualTo("red pleated midi skirt");
  }

  @Test
  void infersCategoryFromNameOnlyWhenStructuredCategoryIsInvalid() {
    ClothingAnalysis incomplete = new ClothingAnalysis(
        "straight-leg linen pants", "beige", "unknown", "solid", "linen",
        "casual", "summer");

    assertThat(service.normalizeClothing(incomplete).category()).isEqualTo("bottom");
  }

  @Test
  void replacesMonochromeShoppingColorForRedBottomByDefault() {
    String selected = "red pleated midi skirt; category: bottom; color: red; occasion: casual";

    assertThat(service.applyShoppingContrastRules(
        "burgundy fitted blouse", selected, "top"))
        .isEqualTo("ivory fitted blouse");
  }

  @Test
  void buildsShoppingQueryWithoutCallingAi() {
    String selected = "red pleated midi skirt; category: bottom; color: red; occasion: casual";

    assertThat(service.createShoppingQuery(selected, "top", "blouse"))
        .isEqualTo("women's ivory fitted cotton blouse");
  }

  @Test
  void favorsPolishedBlouseOverCutoutTank() {
    String selected = "red pleated midi skirt; category: bottom; color: red; pattern: solid; "
        + "material: polyester; occasion: casual; season: all season";
    String polished = "ivory fitted satin blouse; category: top; color: ivory; pattern: solid; "
        + "material: satin; occasion: smart casual; season: all season";
    String cutout = "cropped tank top with cutouts; category: top; color: gray; pattern: solid; "
        + "material: polyester; occasion: casual; season: summer";

    assertThat(service.fashionRuleScore(selected, polished))
        .isGreaterThan(service.fashionRuleScore(selected, cutout));
  }

  @Test
  void rejectsVisualBatchLargerThanSixCandidates() {
    List<OutfitCandidate> candidates = IntStream.range(0, 7)
        .mapToObj(index -> new OutfitCandidate("candidate-" + index, "candidate", "data:image/jpeg;base64,image"))
        .toList();

    assertThatThrownBy(() -> service.scoreOutfits(new OutfitBatchRequest(
        "selected", "data:image/jpeg;base64,image", candidates)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most 6");
  }

  @Test
  void rejectsIncompleteVisualBatch() {
    List<OutfitCandidate> candidates = List.of(
        new OutfitCandidate("candidate-1", "candidate", null));

    assertThatThrownBy(() -> service.scoreOutfits(new OutfitBatchRequest(
        "selected", "data:image/jpeg;base64,image", candidates)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("every candidate image");
  }
}
