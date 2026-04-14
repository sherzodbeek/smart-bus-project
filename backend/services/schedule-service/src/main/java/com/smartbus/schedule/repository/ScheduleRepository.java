package com.smartbus.schedule.repository;

import com.smartbus.schedule.dto.AdminRouteRequest;
import com.smartbus.schedule.dto.LocationResponse;
import com.smartbus.schedule.dto.RouteDefinition;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {

  List<RouteDefinition> findAllRoutes();

  Optional<RouteDefinition> findRouteByStops(String fromStop, String toStop);

  Optional<RouteDefinition> findRouteByCode(String routeCode);

  RouteDefinition createRoute(AdminRouteRequest request);

  RouteDefinition updateRoute(String routeCode, AdminRouteRequest request);

  RouteDefinition updateFare(String routeCode, double unitPrice);

  void deleteRoute(String routeCode);

  List<LocationResponse> findAllLocations();

  Optional<LocationResponse> findLocationById(long id);

  LocationResponse createLocation(String name);

  LocationResponse updateLocation(long id, String name);

  void deleteLocation(long id);
}
