package com.smartbus.schedule.controller;

import com.smartbus.schedule.dto.AdminRouteRequest;
import com.smartbus.schedule.dto.FareUpdateRequest;
import com.smartbus.schedule.dto.LocationRequest;
import com.smartbus.schedule.dto.LocationResponse;
import com.smartbus.schedule.dto.RouteCatalogResponse;
import com.smartbus.schedule.dto.RouteDefinition;
import com.smartbus.schedule.dto.ScheduleQuoteRequest;
import com.smartbus.schedule.dto.ScheduleQuoteResponse;
import com.smartbus.schedule.service.CachedQuoteResponseService;
import com.smartbus.schedule.service.ScheduleCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleOrchestrationController {

  private static final Logger log = LoggerFactory.getLogger(ScheduleOrchestrationController.class);

  private final ScheduleCatalogService scheduleCatalogService;
  private final CachedQuoteResponseService cachedQuoteResponseService;

  public ScheduleOrchestrationController(
      ScheduleCatalogService scheduleCatalogService,
      CachedQuoteResponseService cachedQuoteResponseService
  ) {
    this.scheduleCatalogService = scheduleCatalogService;
    this.cachedQuoteResponseService = cachedQuoteResponseService;
  }

  @GetMapping("/catalog")
  public RouteCatalogResponse catalog() {
    log.info("scheduleCatalogRequest");
    return scheduleCatalogService.catalog();
  }

  @PostMapping("/quote")
  public ScheduleQuoteResponse quote(@Valid @RequestBody ScheduleQuoteRequest request) {
    log.info(
        "scheduleQuoteRequest fromStop={} toStop={} tripDate={} tripType={} passengers={}",
        request.fromStop(),
        request.toStop(),
        request.tripDate(),
        request.tripType(),
        request.passengers()
    );
    return cachedQuoteResponseService.getOrCompute(request, () -> {
      boolean differentStops = !request.fromStop().equalsIgnoreCase(request.toStop());
      boolean seatsAvailable = request.passengers() > 0 && request.passengers() <= 6;
      Optional<RouteDefinition> route = differentStops
          ? scheduleCatalogService.routeDefinition(request.fromStop(), request.toStop())
          : Optional.empty();
      boolean routeSupported = route.isPresent();
      boolean tripAvailable = routeSupported && seatsAvailable;
      boolean returnTripAvailable = !"round-trip".equalsIgnoreCase(request.tripType()) || tripAvailable;
      RouteDefinition routeDefinition = route.orElse(new RouteDefinition(
          "UNAVAILABLE",
          request.fromStop(),
          request.toStop(),
          "-",
          "-",
          0.0,
          0
      ));

      return new ScheduleQuoteResponse(
          tripAvailable,
          returnTripAvailable,
          routeDefinition.routeCode(),
          routeDefinition.departureTime(),
          routeDefinition.arrivalTime(),
          routeDefinition.unitPrice(),
          tripAvailable ? routeDefinition.seatsAvailable() - request.passengers() : 0
      );
    });
  }

  @GetMapping("/admin/locations")
  public List<LocationResponse> locations() {
    log.info("scheduleLocationsRequest");
    return scheduleCatalogService.locations();
  }

  @PostMapping("/admin/locations")
  public LocationResponse createLocation(@Valid @RequestBody LocationRequest request) {
    log.info("scheduleLocationCreateRequest name={}", request.name());
    return scheduleCatalogService.createLocation(request.name());
  }

  @PutMapping("/admin/locations/{id}")
  public LocationResponse updateLocation(@PathVariable long id, @Valid @RequestBody LocationRequest request) {
    log.info("scheduleLocationUpdateRequest id={} name={}", id, request.name());
    return scheduleCatalogService.updateLocation(id, request.name());
  }

  @DeleteMapping("/admin/locations/{id}")
  public Map<String, Object> deleteLocation(@PathVariable long id) {
    log.info("scheduleLocationDeleteRequest id={}", id);
    scheduleCatalogService.deleteLocation(id);
    return Map.of("id", id, "deleted", true);
  }

  @PostMapping("/admin/routes")
  public RouteDefinition createRoute(@Valid @RequestBody AdminRouteRequest request) {
    log.info("scheduleRouteCreateRequest routeCode={} fromStop={} toStop={}", request.routeCode(), request.fromStop(), request.toStop());
    cachedQuoteResponseService.invalidateAll();
    return scheduleCatalogService.createRoute(request);
  }

  @PutMapping("/admin/routes/{routeCode}")
  public RouteDefinition updateRoute(@PathVariable String routeCode, @Valid @RequestBody AdminRouteRequest request) {
    log.info("scheduleRouteUpdateRequest routeCode={} nextRouteCode={} fromStop={} toStop={}", routeCode, request.routeCode(), request.fromStop(), request.toStop());
    cachedQuoteResponseService.invalidateAll();
    return scheduleCatalogService.updateRoute(routeCode, request);
  }

  @DeleteMapping("/admin/routes/{routeCode}")
  public Map<String, Object> deleteRoute(@PathVariable String routeCode) {
    log.info("scheduleRouteDeleteRequest routeCode={}", routeCode);
    scheduleCatalogService.deleteRoute(routeCode);
    cachedQuoteResponseService.invalidateAll();
    return Map.of("routeCode", routeCode, "deleted", true);
  }

  @PostMapping("/admin/routes/{routeCode}/fare")
  public Map<String, Object> updateFare(@PathVariable String routeCode, @Valid @RequestBody FareUpdateRequest request) {
    log.info("scheduleFareUpdateRequest routeCode={} unitPrice={}", routeCode, request.unitPrice());
    RouteDefinition routeDefinition = scheduleCatalogService.refreshFare(routeCode, request.unitPrice());
    cachedQuoteResponseService.invalidateAll();
    return Map.of(
        "routeCode", routeDefinition.routeCode(),
        "unitPrice", routeDefinition.unitPrice(),
        "cacheInvalidated", true
    );
  }
}
