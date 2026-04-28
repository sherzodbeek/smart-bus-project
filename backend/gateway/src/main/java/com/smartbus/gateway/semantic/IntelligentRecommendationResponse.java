package com.smartbus.gateway.semantic;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response body for {@code GET /api/v1/frontend/intelligent/recommend}.
 */
public record IntelligentRecommendationResponse(
    String email,
    List<EnrichedRecommendationRecord> recommendations,
    boolean coldStart,
    String modelVersion,
    Map<String, Object> semanticFeatures,
    Map<String, Object> enrichmentStats,
    OffsetDateTime generatedAt
) {
}
