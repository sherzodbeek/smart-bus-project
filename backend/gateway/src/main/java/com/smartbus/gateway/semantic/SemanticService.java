package com.smartbus.gateway.semantic;

import com.smartbus.gateway.config.MlServerProperties;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Proxy service that forwards semantic/SPARQL requests to the Python Flask
 * knowledge-graph server and returns the raw JSON response map.
 *
 * <p>All methods return a non-null map; on Flask unavailability they return an
 * error map so the caller can degrade gracefully without throwing.
 */
@Service
public class SemanticService {

  private static final Logger log = LoggerFactory.getLogger(SemanticService.class);

  private final RestClient restClient;
  private final MlServerProperties mlProperties;

  public SemanticService(
      RestClient.Builder restClientBuilder,
      MlServerProperties mlProperties
  ) {
    this.restClient   = restClientBuilder.build();
    this.mlProperties = mlProperties;
  }

  // ── Health ──────────────────────────────────────────────────────────────────

  public Map<String, Object> health() {
    return getJson("/api/semantic/health", Collections.emptyMap());
  }

  // ── Route queries ───────────────────────────────────────────────────────────

  public Map<String, Object> routesFromStop(String stopName) {
    return getJson("/api/semantic/routes", Map.of("from", stopName));
  }

  public Map<String, Object> routesInZone(String stopName) {
    return getJson("/api/semantic/routes/zone", Map.of("stop", stopName));
  }

  // ── User-specific inference ─────────────────────────────────────────────────

  public Map<String, Object> candidates(String email) {
    return getJson("/api/semantic/candidates", Map.of("email", email));
  }

  public Map<String, Object> insights(String email) {
    return getJson("/api/semantic/insights", Map.of("email", email));
  }

  // ── Generic entity traversal ────────────────────────────────────────────────

  public Map<String, Object> findRelated(String entity, String relationship) {
    return getJson("/api/semantic/find-related",
        Map.of("entity", entity, "relationship", relationship));
  }

  // ── Intelligent (AI + Ontology) ────────────────────────────────────────────

  public Map<String, Object> intelligentRecommend(String email, int n) {
    return getJson("/api/intelligent/recommend",
        Map.of("email", email, "n", String.valueOf(n)));
  }

  public Map<String, Object> intelligentExplain(String email, String routeCode) {
    return getJson("/api/intelligent/explain",
        Map.of("email", email, "route", routeCode));
  }

  // ── Raw SPARQL passthrough ──────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  public Map<String, Object> executeSparql(Map<String, Object> body) {
    var uri = mlProperties.server().resolve("/api/semantic/query");
    try {
      Map<String, Object> result = restClient.post()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      return result != null ? result : Collections.emptyMap();
    } catch (RestClientException ex) {
      log.warn("semanticQueryFailed error={}", ex.getMessage());
      return Map.of("error", "Knowledge graph unavailable: " + ex.getMessage());
    }
  }

  // ── Internal helpers ────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private Map<String, Object> getJson(String path, Map<String, String> params) {
    var builder = UriComponentsBuilder.fromUri(mlProperties.server().resolve(path));
    params.forEach(builder::queryParam);
    var uri = builder.build().toUri();

    try {
      Map<String, Object> result = restClient.get()
          .uri(uri)
          .retrieve()
          .body(Map.class);
      return result != null ? result : Collections.emptyMap();
    } catch (RestClientException ex) {
      log.warn("semanticGetFailed path={} error={}", path, ex.getMessage());
      return Map.of("error", "Knowledge graph unavailable: " + ex.getMessage());
    }
  }
}
