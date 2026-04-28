package com.smartbus.gateway.semantic;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Post-processes enriched AI-Ontology recommendations returned by the Flask
 * intelligent endpoint.
 *
 * <p>The Flask bridge already applies the semantic boost and stores results in
 * the knowledge graph.  This class handles the gateway-side concerns:
 * <ul>
 *   <li>Maps raw Flask JSON to typed {@link EnrichedRecommendationRecord} objects.
 *   <li>Applies the display-name lookup (same set as {@code DecisionEngine}).
 *   <li>Generates a human-readable composite reason label combining the ML
 *       reason and any ontology-derived reason codes.
 * </ul>
 */
public final class EnrichedDecisionEngine {

  private static final double MIN_SCORE = 0.01;

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

  private EnrichedDecisionEngine() {
  }

  /**
   * Convert the {@code recommendations} array from the Flask intelligent
   * endpoint into typed records, filtering low-signal results.
   */
  @SuppressWarnings("unchecked")
  public static List<EnrichedRecommendationRecord> process(
      List<Map<String, Object>> rawList,
      String modelVersion,
      boolean isColdStart
  ) {
    if (rawList == null) return Collections.emptyList();

    return rawList.stream()
        .filter(r -> toDouble(r.get("enriched_score")) >= MIN_SCORE)
        .map(r -> {
          String code          = String.valueOf(r.get("route_code"));
          double mlScore       = toDouble(r.get("ml_score"));
          double semanticBoost = toDouble(r.get("semantic_boost"));
          double enrichedScore = toDouble(r.get("enriched_score"));
          double cfScore       = toDouble(r.get("cf_score"));
          double cbScore       = toDouble(r.get("cb_score"));
          String reason        = String.valueOf(r.getOrDefault("reason", ""));
          List<String> semReasons =
              (List<String>) r.getOrDefault("semantic_reasons", Collections.emptyList());

          return new EnrichedRecommendationRecord(
              code,
              displayName(code),
              mlScore,
              semanticBoost,
              enrichedScore,
              cfScore,
              cbScore,
              reason,
              confidenceLabel(enrichedScore),
              reasonLabel(reason, semReasons, isColdStart),
              semReasons,
              modelVersion,
              isColdStart
          );
        })
        .toList();
  }

  private static String displayName(String code) {
    return ROUTE_DISPLAY_NAMES.getOrDefault(code, code);
  }

  private static String confidenceLabel(double score) {
    if (score >= 0.60) return "HIGH";
    if (score >= 0.30) return "MEDIUM";
    return "LOW";
  }

  private static String reasonLabel(
      String reason,
      List<String> semReasons,
      boolean isColdStart
  ) {
    if (isColdStart) return "Popular route";

    StringBuilder sb = new StringBuilder();

    String mlPart = switch (reason) {
      case "collaborative_match" -> "Users like you booked this";
      case "content_match"       -> "Matches your travel preferences";
      case "popularity_fallback" -> "Popular route";
      default -> {
        if (reason.startsWith("collaborative_match")) yield "Users like you booked this";
        if (reason.startsWith("content_match"))       yield "Matches your travel preferences";
        yield "Recommended for you";
      }
    };
    sb.append(mlPart);

    if (semReasons.contains("origin_preference_match")) {
      sb.append(" · departs from your preferred stop");
    }
    if (semReasons.contains("frequently_travelled")) {
      sb.append(" · you travel this route often");
    }

    return sb.toString();
  }

  private static double toDouble(Object v) {
    if (v instanceof Number n) return n.doubleValue();
    try { return Double.parseDouble(String.valueOf(v)); }
    catch (NumberFormatException e) { return 0.0; }
  }
}
