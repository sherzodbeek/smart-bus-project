package com.smartbus.schedule.service;

import com.smartbus.schedule.dto.AdminRouteRequest;
import com.smartbus.schedule.dto.LocationResponse;
import com.smartbus.schedule.util.HtmlSanitizer;
import com.smartbus.schedule.dto.RouteCatalogResponse;
import com.smartbus.schedule.dto.RouteDefinition;
import com.smartbus.schedule.repository.ScheduleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleCatalogService {

  private final ScheduleRepository scheduleRepository;

  public ScheduleCatalogService(ScheduleRepository scheduleRepository) {
    this.scheduleRepository = scheduleRepository;
  }

  @Cacheable("routeCatalog")
  public RouteCatalogResponse catalog() {
    return new RouteCatalogResponse(scheduleRepository.findAllRoutes());
  }

  @Cacheable(cacheNames = "routeDefinition", key = "#fromStop + '->' + #toStop")
  public Optional<RouteDefinition> routeDefinition(String fromStop, String toStop) {
    return scheduleRepository.findRouteByStops(fromStop, toStop);
  }

  @CacheEvict(cacheNames = {"routeCatalog", "routeDefinition"}, allEntries = true)
  public RouteDefinition refreshFare(String routeCode, double unitPrice) {
    return scheduleRepository.updateFare(routeCode, unitPrice);
  }

  @CacheEvict(cacheNames = {"routeCatalog", "routeDefinition"}, allEntries = true)
  public RouteDefinition createRoute(AdminRouteRequest request) {
    return scheduleRepository.createRoute(request);
  }

  @CacheEvict(cacheNames = {"routeCatalog", "routeDefinition"}, allEntries = true)
  public RouteDefinition updateRoute(String routeCode, AdminRouteRequest request) {
    return scheduleRepository.updateRoute(routeCode, request);
  }

  @CacheEvict(cacheNames = {"routeCatalog", "routeDefinition"}, allEntries = true)
  public void deleteRoute(String routeCode) {
    scheduleRepository.deleteRoute(routeCode);
  }

  public RouteDefinition requireRoute(String routeCode) {
    return scheduleRepository.findRouteByCode(routeCode)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route was not found."));
  }

  @Cacheable("locationCatalog")
  public List<LocationResponse> locations() {
    return scheduleRepository.findAllLocations();
  }

  @Cacheable(cacheNames = "locationById", key = "#id")
  public LocationResponse requireLocation(long id) {
    return scheduleRepository.findLocationById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location was not found."));
  }

  @CacheEvict(cacheNames = {"locationCatalog", "locationById"}, allEntries = true)
  public LocationResponse createLocation(String name) {
    return scheduleRepository.createLocation(HtmlSanitizer.strip(name));
  }

  @CacheEvict(cacheNames = {"locationCatalog", "locationById"}, allEntries = true)
  public LocationResponse updateLocation(long id, String name) {
    return scheduleRepository.updateLocation(id, HtmlSanitizer.strip(name));
  }

  @CacheEvict(cacheNames = {"locationCatalog", "locationById"}, allEntries = true)
  public void deleteLocation(long id) {
    scheduleRepository.deleteLocation(id);
  }
}
