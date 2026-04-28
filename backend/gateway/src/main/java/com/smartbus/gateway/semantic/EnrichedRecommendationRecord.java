package com.smartbus.gateway.semantic;

import java.util.List;

/**
 * Immutable view of a single enriched route recommendation combining ML
 * predictions with ontology-derived semantic features.
 */
public record EnrichedRecommendationRecord(
    String routeCode,
    String displayName,
    double mlScore,
    double semanticBoost,
    double enrichedScore,
    double cfScore,
    double cbScore,
    String reason,
    String confidence,
    String reasonLabel,
    List<String> semanticReasons,
    String modelVersion,
    boolean coldStart
) {
}
