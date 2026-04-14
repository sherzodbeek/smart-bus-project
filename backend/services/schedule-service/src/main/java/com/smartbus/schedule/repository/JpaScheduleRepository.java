package com.smartbus.schedule.repository;

import com.smartbus.schedule.config.ScheduleServiceProperties;
import com.smartbus.schedule.dto.AdminRouteRequest;
import com.smartbus.schedule.dto.LocationResponse;
import com.smartbus.schedule.dto.RouteDefinition;
import com.smartbus.schedule.entity.ScheduleLocationEntity;
import com.smartbus.schedule.entity.ScheduleRouteEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JpaScheduleRepository implements ScheduleRepository {

  private final ScheduleRouteEntityRepository routeRepository;
  private final ScheduleLocationEntityRepository locationRepository;
  private final ScheduleServiceProperties properties;

  public JpaScheduleRepository(
      ScheduleRouteEntityRepository routeRepository,
      ScheduleLocationEntityRepository locationRepository,
      ScheduleServiceProperties properties
  ) {
    this.routeRepository = routeRepository;
    this.locationRepository = locationRepository;
    this.properties = properties;
  }

  @Override
  public List<RouteDefinition> findAllRoutes() {
    simulateDataStoreLatency();
    return routeRepository.findAllByOrderByRouteCodeAsc().stream().map(this::toDefinition).toList();
  }

  @Override
  public Optional<RouteDefinition> findRouteByStops(String fromStop, String toStop) {
    simulateDataStoreLatency();
    return routeRepository.findAllByStops(fromStop, toStop).stream().findFirst().map(this::toDefinition);
  }

  @Override
  public Optional<RouteDefinition> findRouteByCode(String routeCode) {
    simulateDataStoreLatency();
    return routeRepository.findByRouteCode(routeCode).map(this::toDefinition);
  }

  @Override
  public RouteDefinition createRoute(AdminRouteRequest request) {
    simulateDataStoreLatency();
    ScheduleLocationEntity fromLocation = requireLocation(request.fromStop());
    ScheduleLocationEntity toLocation = requireLocation(request.toStop());
    validateDistinctLocations(fromLocation, toLocation);

    ScheduleRouteEntity entity = new ScheduleRouteEntity();
    applyRoute(entity, normalizeCode(request.routeCode()), fromLocation, toLocation, request);
    routeRepository.save(entity);
    return requireDetailedRoute(normalizeCode(request.routeCode()));
  }

  @Override
  public RouteDefinition updateRoute(String routeCode, AdminRouteRequest request) {
    simulateDataStoreLatency();
    ScheduleRouteEntity entity = routeRepository.findByRouteCode(routeCode)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route was not found."));
    ScheduleLocationEntity fromLocation = requireLocation(request.fromStop());
    ScheduleLocationEntity toLocation = requireLocation(request.toStop());
    validateDistinctLocations(fromLocation, toLocation);

    if (!entity.getRouteCode().equals(normalizeCode(request.routeCode()))) {
      routeRepository.delete(entity);
      entity = new ScheduleRouteEntity();
    }
    applyRoute(entity, normalizeCode(request.routeCode()), fromLocation, toLocation, request);
    routeRepository.save(entity);
    return requireDetailedRoute(normalizeCode(request.routeCode()));
  }

  @Override
  public RouteDefinition updateFare(String routeCode, double unitPrice) {
    simulateDataStoreLatency();
    ScheduleRouteEntity entity = routeRepository.findByRouteCode(routeCode)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route was not found."));
    entity.setUnitPrice(unitPrice);
    routeRepository.save(entity);
    return requireDetailedRoute(routeCode);
  }

  @Override
  public void deleteRoute(String routeCode) {
    simulateDataStoreLatency();
    if (!routeRepository.existsById(routeCode)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Route was not found.");
    }
    routeRepository.deleteById(routeCode);
  }

  @Override
  public List<LocationResponse> findAllLocations() {
    simulateDataStoreLatency();
    return locationRepository.findAllByOrderByNameAsc().stream()
        .map(location -> new LocationResponse(location.getId(), location.getName()))
        .toList();
  }

  @Override
  public Optional<LocationResponse> findLocationById(long id) {
    simulateDataStoreLatency();
    return locationRepository.findById(id)
        .map(location -> new LocationResponse(location.getId(), location.getName()));
  }

  @Override
  public LocationResponse createLocation(String name) {
    simulateDataStoreLatency();
    ScheduleLocationEntity existing = locationRepository.findByNameIgnoreCase(name.trim()).orElse(null);
    if (existing != null) {
      return new LocationResponse(existing.getId(), existing.getName());
    }
    ScheduleLocationEntity entity = new ScheduleLocationEntity();
    entity.setName(name.trim());
    ScheduleLocationEntity saved = locationRepository.save(entity);
    return new LocationResponse(saved.getId(), saved.getName());
  }

  @Override
  public LocationResponse updateLocation(long id, String name) {
    simulateDataStoreLatency();
    ScheduleLocationEntity entity = locationRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location was not found."));
    String normalizedName = name.trim();
    if (locationRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Another location already uses that name.");
    }
    entity.setName(normalizedName);
    ScheduleLocationEntity saved = locationRepository.save(entity);
    return new LocationResponse(saved.getId(), saved.getName());
  }

  @Override
  public void deleteLocation(long id) {
    simulateDataStoreLatency();
    if (!locationRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location was not found.");
    }
    if (routeRepository.countByFromLocation_IdOrToLocation_Id(id, id) > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Location is used by existing routes and cannot be deleted.");
    }
    locationRepository.deleteById(id);
  }

  private void applyRoute(
      ScheduleRouteEntity entity,
      String routeCode,
      ScheduleLocationEntity fromLocation,
      ScheduleLocationEntity toLocation,
      AdminRouteRequest request
  ) {
    entity.setRouteCode(routeCode);
    entity.setFromLocation(fromLocation);
    entity.setToLocation(toLocation);
    entity.setDepartureTime(request.departureTime().trim());
    entity.setArrivalTime(request.arrivalTime().trim());
    entity.setUnitPrice(request.unitPrice());
    entity.setSeatsAvailable(request.seatsAvailable());
  }

  private String normalizeCode(String routeCode) {
    return routeCode.trim().toUpperCase();
  }

  private ScheduleLocationEntity requireLocation(String name) {
    return locationRepository.findByNameIgnoreCase(name.trim())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location was not found: " + name));
  }

  private void validateDistinctLocations(ScheduleLocationEntity fromLocation, ScheduleLocationEntity toLocation) {
    if (fromLocation.getId().equals(toLocation.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route start and end locations must be different.");
    }
  }

  private RouteDefinition toDefinition(ScheduleRouteEntity entity) {
    return new RouteDefinition(
        entity.getRouteCode(),
        entity.getFromLocation().getName(),
        entity.getToLocation().getName(),
        entity.getDepartureTime(),
        entity.getArrivalTime(),
        entity.getUnitPrice(),
        entity.getSeatsAvailable()
    );
  }

  private RouteDefinition requireDetailedRoute(String routeCode) {
    return routeRepository.findByRouteCode(routeCode)
        .map(this::toDefinition)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Route was not found after update."));
  }

  private void simulateDataStoreLatency() {
    try {
      Thread.sleep(properties.simulatedLatency().toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
