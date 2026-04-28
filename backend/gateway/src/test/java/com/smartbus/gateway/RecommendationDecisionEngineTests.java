package com.smartbus.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbus.gateway.recommendation.DecisionEngine;
import com.smartbus.gateway.recommendation.DecisionEngine.RawRecommendation;
import com.smartbus.gateway.recommendation.RecommendationRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationDecisionEngineTests {

  @Test
  void filtersLowScoreRecommendations() {
    List<RawRecommendation> raw = List.of(
        new RawRecommendation("SB-101", 0.72, 0.14, 0.97, "content_match"),
        new RawRecommendation("SB-202", 0.005, 0.0, 0.01, "content_match") // below threshold
    );

    List<RecommendationRecord> result = DecisionEngine.process(raw, "1.0.0", false);

    assertEquals(1, result.size());
    assertEquals("SB-101", result.get(0).routeCode());
  }

  @Test
  void assignsCorrectConfidenceLabels() {
    List<RawRecommendation> raw = List.of(
        new RawRecommendation("SB-101", 0.75, 0.14, 0.97, "content_match"),
        new RawRecommendation("SB-202", 0.40, 0.20, 0.50, "collaborative_match"),
        new RawRecommendation("SB-303", 0.15, 0.05, 0.20, "content_match")
    );

    List<RecommendationRecord> result = DecisionEngine.process(raw, "1.0.0", false);

    assertEquals("HIGH",   result.get(0).confidence());
    assertEquals("MEDIUM", result.get(1).confidence());
    assertEquals("LOW",    result.get(2).confidence());
  }

  @Test
  void enrichesDisplayNameForKnownRoutes() {
    List<RawRecommendation> raw = List.of(
        new RawRecommendation("SB-101", 0.72, 0.14, 0.97, "content_match")
    );

    List<RecommendationRecord> result = DecisionEngine.process(raw, "1.0.0", false);

    assertEquals("Downtown Terminal → Airport Station", result.get(0).displayName());
  }

  @Test
  void coldStartUsesPopularityFallbackLabel() {
    List<RawRecommendation> raw = List.of(
        new RawRecommendation("SB-201", 1.0, 0.0, 0.0, "popularity_fallback")
    );

    List<RecommendationRecord> result = DecisionEngine.process(raw, "1.0.0", true);

    assertTrue(result.get(0).coldStart());
    assertEquals("Popular route", result.get(0).reasonLabel());
  }

  @Test
  void collaborativeMatchGetsCorrectReasonLabel() {
    List<RawRecommendation> raw = List.of(
        new RawRecommendation("SB-202", 0.55, 0.45, 0.58, "collaborative_match")
    );

    List<RecommendationRecord> result = DecisionEngine.process(raw, "1.0.0", false);

    assertFalse(result.get(0).coldStart());
    assertEquals("Users like you booked this", result.get(0).reasonLabel());
  }

  @Test
  void emptyInputReturnsEmptyList() {
    List<RecommendationRecord> result = DecisionEngine.process(List.of(), "1.0.0", false);
    assertTrue(result.isEmpty());
  }
}
