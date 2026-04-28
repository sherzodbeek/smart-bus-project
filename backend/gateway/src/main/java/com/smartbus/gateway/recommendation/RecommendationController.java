package com.smartbus.gateway.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/frontend/recommendations")
public class RecommendationController {

  private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

  private final RecommendationService service;

  public RecommendationController(RecommendationService service) {
    this.service = service;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public RecommendationResponse getRecommendations(Authentication authentication) {
    String email = authentication.getName();
    log.info("recommendationsRequest email={}", email);
    return service.recommend(email);
  }
}
