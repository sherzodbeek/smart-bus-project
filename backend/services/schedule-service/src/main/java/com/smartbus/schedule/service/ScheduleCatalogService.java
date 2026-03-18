package com.smartbus.schedule.service;

import com.smartbus.schedule.dto.AdminRouteRequest;
import com.smartbus.schedule.dto.LocationResponse;
import com.smartbus.schedule.dto.RouteCatalogResponse;
import com.smartbus.schedule.dto.RouteDefinition;
import com.smartbus.schedule.repository.ScheduleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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

  public List<LocationResponse> locations() {
    return scheduleRepository.findAllLocations();
  }

  @CacheEvict(cacheNames = {"routeCatalog", "routeDefinition"}, allEntries = true)
  public LocationResponse createLocation(String name) {
    return scheduleRepository.createLocation(name);
  }

  public LocationResponse updateLocation(long id, String name) {
    return scheduleRepository.updateLocation(id, name);
  }

  public void deleteLocation(long id) {
    scheduleRepository.deleteLocation(id);
  }
}
