package com.smartbus.gateway.recommendation;

import java.util.List;
import java.util.Map;

/**
 * Intelligent decision-making layer that post-processes raw ML predictions.
 *
 * Applies three rules to each recommendation before it is returned to the frontend:
 *   1. Confidence labelling  — maps hybrid score to a human-readable label.
 *   2. Reason enrichment     — converts the internal reason code to a display string.
 *   3. Low-signal filter     — drops recommendations below the minimum score threshold.
 */
public final class DecisionEngine {

  private static final double MIN_SCORE_THRESHOLD = 0.01;

  private static final Map<String, String> ROUTE_DISPLAY_NAMES = Map.of(
      "SB-101", "Downtown Terminal → Airport Station",
      "SB-102", "Downtown Terminal → University",
      "SB-103", "Downtown Terminal → Harbor",
      "SB-201", "Airport Station → City Center",
      "SB-202", "University → City Center",
      "SB-203", "City Center → Bus Depot",
      "SB-301", "Harbor → Downtown Terminal",
      "SB-302", "Bus Depot → Harbor",
      "SB-303", "City Center → Airport Station",
      "SB-304", "University → Airport Station"
  );

  private DecisionEngine() {
  }

  /**
   * Process a list of raw ML recommendations into enriched recommendation records.
   * Filters, labels, and enriches each recommendation.
   */
  public static List<RecommendationRecord> process(
      List<RawRecommendation> raw,
      String modelVersion,
      boolean isColdStart
  ) {
    return raw.stream()
        .filter(r -> r.hybridScore() >= MIN_SCORE_THRESHOLD)
        .map(r -> new RecommendationRecord(
            r.routeCode(),
            displayName(r.routeCode()),
            r.hybridScore(),
            r.cfScore(),
            r.cbScore(),
            r.reason(),
            confidenceLabel(r.hybridScore()),
            reasonLabel(r.reason(), isColdStart),
            modelVersion,
            isColdStart
        ))
        .toList();
  }

  private static String displayName(String routeCode) {
    return ROUTE_DISPLAY_NAMES.getOrDefault(routeCode, routeCode);
  }

  private static String confidenceLabel(double score) {
    if (score >= 0.60) return "HIGH";
    if (score >= 0.30) return "MEDIUM";
    return "LOW";
  }

  private static String reasonLabel(String reason, boolean isColdStart) {
    if (isColdStart) return "Popular route";
    return switch (reason) {
      case "collaborative_match" -> "Users like you booked this";
      case "content_match"       -> "Matches your travel preferences";
      case "popularity_fallback" -> "Popular route";
      default                    -> "Recommended for you";
    };
  }

  // ── Inner types ──────────────────────────────────────────────────────────

  public record RawRecommendation(
      String routeCode,
      double hybridScore,
      double cfScore,
      double cbScore,
      String reason
  ) {
  }
}
