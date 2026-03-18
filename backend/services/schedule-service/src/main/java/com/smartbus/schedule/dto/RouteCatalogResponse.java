package com.smartbus.schedule.dto;

import java.util.List;

public record RouteCatalogResponse(
    List<RouteDefinition> routes
) {
}
