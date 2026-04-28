package com.smartbus.gateway.semantic;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST facade for the SmartBus semantic/SPARQL layer.
 *
 * <p>All endpoints proxy to the Python Flask knowledge-graph server.
 * User-specific endpoints (candidates, insights) derive the email from the
 * JWT token so the caller does not supply it.
 *
 * <p>Endpoint summary:
 * <pre>
 *   GET  /api/v1/frontend/semantic/health           — KG stats + validation
 *   GET  /api/v1/frontend/semantic/routes           — routes departing from a stop
 *   GET  /api/v1/frontend/semantic/routes/zone      — routes in same zone as stop
 *   GET  /api/v1/frontend/semantic/candidates       — R4 candidate routes for current user
 *   GET  /api/v1/frontend/semantic/insights         — R1+R2+R4 inference for current user
 *   GET  /api/v1/frontend/semantic/find-related     — generic entity–relationship traversal
 *   POST /api/v1/frontend/semantic/query            — raw SPARQL SELECT passthrough
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/frontend/semantic")
public class SemanticController {

  private static final Logger log = LoggerFactory.getLogger(SemanticController.class);

  private final SemanticService service;

  public SemanticController(SemanticService service) {
    this.service = service;
  }

  @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> health() {
    return service.health();
  }

  /**
   * Q1 — routes departing from a named stop.
   *
   * @param from stop display name, e.g. "Downtown Terminal"
   */
  @GetMapping(value = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> routesFromStop(@RequestParam("from") String from) {
    log.debug("semanticRoutesFromStop stop={}", from);
    return service.routesFromStop(from);
  }

  /**
   * Q2 + R3 — routes in the same geographic zone as the anchor stop.
   *
   * @param stop stop display name
   */
  @GetMapping(value = "/routes/zone", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> routesInZone(@RequestParam("stop") String stop) {
    log.debug("semanticRoutesInZone stop={}", stop);
    return service.routesInZone(stop);
  }

  /**
   * R4 — candidate routes derived from the authenticated user's preferred origin stop.
   */
  @GetMapping(value = "/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> candidates(Authentication authentication) {
    String email = authentication.getName();
    log.info("semanticCandidates email={}", email);
    return service.candidates(email);
  }

  /**
   * Combined inference summary (R1 + R2 + R4 + stored recommendations) for the
   * authenticated user.
   */
  @GetMapping(value = "/insights", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> insights(Authentication authentication) {
    String email = authentication.getName();
    log.info("semanticInsights email={}", email);
    return service.insights(email);
  }

  /**
   * Generic entity–relationship traversal.
   *
   * @param entity       display label of the entity, e.g. "Downtown Terminal"
   * @param relationship relationship name, e.g. "departsFrom"
   */
  @GetMapping(value = "/find-related", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> findRelated(
      @RequestParam("entity") String entity,
      @RequestParam("relationship") String relationship
  ) {
    log.debug("semanticFindRelated entity={} relationship={}", entity, relationship);
    return service.findRelated(entity, relationship);
  }

  /**
   * Raw SPARQL SELECT passthrough (admin / demo use).
   *
   * @param body JSON object containing {@code "sparql"} key
   */
  @PostMapping(
      value = "/query",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public Map<String, Object> query(@RequestBody Map<String, Object> body) {
    log.debug("semanticQuery");
    return service.executeSparql(body);
  }
}
