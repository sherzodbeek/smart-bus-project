package com.smartbus.gateway.recommendation;

import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationResponse(
    String customerEmail,
    List<RecommendationRecord> recommendations,
    boolean isColdStart,
    String modelVersion,
    OffsetDateTime generatedAt
) {
}
