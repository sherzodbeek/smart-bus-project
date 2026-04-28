package com.smartbus.gateway.semantic;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified endpoint for AI + Ontology integrated recommendations.
 *
 * <p>Unlike the basic recommendation endpoint ({@code /api/v1/frontend/recommendations}),
 * this controller applies semantic enrichment: ontology-derived features boost ML scores
 * and prediction results are stored back in the RDF knowledge graph.
 *
 * <p>Endpoint summary:
 * <pre>
 *   GET /api/v1/frontend/intelligent/recommend    — enriched recommendations for current user
 *   GET /api/v1/frontend/intelligent/explain      — per-route reasoning breakdown
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/frontend/intelligent")
public class IntelligentRecommendationController {

  private static final Logger log =
      LoggerFactory.getLogger(IntelligentRecommendationController.class);

  private static final int DEFAULT_N = 3;

  private final SemanticService service;

  public IntelligentRecommendationController(SemanticService service) {
    this.service = service;
  }

  /**
   * Returns AI + Ontology enriched route recommendations for the authenticated user.
   *
   * <p>Each recommendation includes:
   * <ul>
   *   <li>{@code ml_score}       — raw hybrid collaborative/content score
   *   <li>{@code semantic_boost} — ontology-derived score addition (R1 + R4)
   *   <li>{@code enriched_score} — final score used for ranking
   *   <li>{@code semantic_reasons} — which ontology rules contributed
   * </ul>
   */
  @GetMapping(value = "/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
  public IntelligentRecommendationResponse recommend(
      Authentication authentication,
      @RequestParam(value = "n", defaultValue = "3") int n
  ) {
    String email = authentication.getName();
    log.info("intelligentRecommendRequest email={} n={}", email, n);

    Map<String, Object> raw = service.intelligentRecommend(email, n);
    return buildResponse(email, raw);
  }

  /**
   * Returns a detailed reasoning explanation for a specific route recommendation.
   *
   * @param route the route code, e.g. {@code SB-101}
   */
  @GetMapping(value = "/explain", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> explain(
      Authentication authentication,
      @RequestParam("route") String route
  ) {
    String email = authentication.getName();
    log.info("intelligentExplainRequest email={} route={}", email, route);
    return service.intelligentExplain(email, route);
  }

  // ── Mapping ────────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private IntelligentRecommendationResponse buildResponse(
      String email,
      Map<String, Object> raw
  ) {
    if (raw.containsKey("error")) {
      log.warn("intelligentRecommendFailed email={} error={}", email, raw.get("error"));
      return new IntelligentRecommendationResponse(
          email, Collections.emptyList(), false, "unavailable",
          Collections.emptyMap(), Collections.emptyMap(), OffsetDateTime.now()
      );
    }

    boolean isColdStart   = Boolean.TRUE.equals(raw.get("is_cold_start"));
    String  modelVersion  = String.valueOf(raw.getOrDefault("model_version", "unknown"));
    Map<String, Object> semFeatures   = (Map<String, Object>) raw.getOrDefault("semantic_features", Collections.emptyMap());
    Map<String, Object> enrichStats   = (Map<String, Object>) raw.getOrDefault("enrichment_stats", Collections.emptyMap());
    List<Map<String, Object>> rawRecs = (List<Map<String, Object>>) raw.getOrDefault("recommendations", Collections.emptyList());

    List<EnrichedRecommendationRecord> records =
        EnrichedDecisionEngine.process(rawRecs, modelVersion, isColdStart);

    return new IntelligentRecommendationResponse(
        email, records, isColdStart, modelVersion,
        semFeatures, enrichStats, OffsetDateTime.now()
    );
  }
}
