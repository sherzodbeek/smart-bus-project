package com.smartbus.gateway.recommendation;

public record RecommendationRecord(
    String routeCode,
    String displayName,
    double hybridScore,
    double cfScore,
    double cbScore,
    String reason,
    String confidence,
    String reasonLabel,
    String modelVersion,
    boolean coldStart
) {
}
