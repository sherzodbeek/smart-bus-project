package com.smartbus.gateway.recommendation;

import com.smartbus.gateway.config.MlServerProperties;
import com.smartbus.gateway.recommendation.DecisionEngine.RawRecommendation;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

  private static final int DEFAULT_N = 3;

  private final RestClient restClient;
  private final MlServerProperties mlProperties;
  private final RecommendationEntityRepository repository;

  public RecommendationService(
      RestClient.Builder restClientBuilder,
      MlServerProperties mlProperties,
      RecommendationEntityRepository repository
  ) {
    this.restClient   = restClientBuilder.build();
    this.mlProperties = mlProperties;
    this.repository   = repository;
  }

  /**
   * Fetch recommendations for the given email from the ML server,
   * apply decision-engine enrichment, persist results, and return.
   *
   * <p>If the ML server is unavailable, returns an empty list without
   * failing the request — the frontend degrades gracefully.
   */
  @Transactional
  public RecommendationResponse recommend(String email) {
    long t0 = System.currentTimeMillis();

    MlResponse mlResponse = callMlServer(email);
    if (mlResponse == null) {
      log.warn("recommendationsMlUnavailable email={}", email);
      return new RecommendationResponse(email, Collections.emptyList(), false, "unavailable", OffsetDateTime.now());
    }

    List<RecommendationRecord> processed = DecisionEngine.process(
        mlResponse.recommendations(),
        mlResponse.modelVersion(),
        mlResponse.isColdStart()
    );

    persist(email, processed, mlResponse.modelVersion(), mlResponse.isColdStart());

    long elapsed = System.currentTimeMillis() - t0;
    log.info(
        "recommendationsServed email={} count={} cold_start={} model={} latency_ms={}",
        email, processed.size(), mlResponse.isColdStart(), mlResponse.modelVersion(), elapsed
    );

    return new RecommendationResponse(
        email, processed, mlResponse.isColdStart(), mlResponse.modelVersion(), OffsetDateTime.now()
    );
  }

  // ── ML server call ─────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private MlResponse callMlServer(String email) {
    var uri = UriComponentsBuilder
        .fromUri(mlProperties.server().resolve("/api/ml/recommend"))
        .queryParam("email", email)
        .queryParam("n", DEFAULT_N)
        .build()
        .toUri();

    try {
      Map<String, Object> body = restClient.get()
          .uri(uri)
          .retrieve()
          .body(Map.class);

      if (body == null) return null;

      boolean isColdStart  = Boolean.TRUE.equals(body.get("is_cold_start"));
      String modelVersion  = String.valueOf(body.getOrDefault("model_version", "unknown"));
      List<Map<String, Object>> rawList =
          (List<Map<String, Object>>) body.getOrDefault("recommendations", Collections.emptyList());

      List<RawRecommendation> recs = rawList.stream()
          .map(r -> new RawRecommendation(
              String.valueOf(r.get("route_code")),
              toDouble(r.get("hybrid_score")),
              toDouble(r.get("cf_score")),
              toDouble(r.get("cb_score")),
              String.valueOf(r.getOrDefault("reason", "unknown"))
          ))
          .collect(Collectors.toList());

      return new MlResponse(recs, modelVersion, isColdStart);

    } catch (RestClientException ex) {
      log.warn("recommendationsMlCallFailed email={} error={}", email, ex.getMessage());
      return null;
    }
  }

  // ── Persistence ────────────────────────────────────────────────────────────

  private void persist(
      String email,
      List<RecommendationRecord> records,
      String modelVersion,
      boolean isColdStart
  ) {
    List<RecommendationEntity> entities = records.stream().map(rec -> {
      RecommendationEntity e = new RecommendationEntity();
      e.setCustomerEmail(email);
      e.setRouteCode(rec.routeCode());
      e.setHybridScore(rec.hybridScore());
      e.setCfScore(rec.cfScore());
      e.setCbScore(rec.cbScore());
      e.setReason(rec.reason());
      e.setConfidence(rec.confidence());
      e.setModelVersion(modelVersion);
      e.setColdStart(isColdStart);
      return e;
    }).toList();
    repository.saveAll(entities);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private static double toDouble(Object value) {
    if (value instanceof Number n) return n.doubleValue();
    try { return Double.parseDouble(String.valueOf(value)); }
    catch (NumberFormatException e) { return 0.0; }
  }

  // ── Inner records ──────────────────────────────────────────────────────────

  private record MlResponse(
      List<RawRecommendation> recommendations,
      String modelVersion,
      boolean isColdStart
  ) {}
}
