package com.smartbus.schedule.repository;

import com.smartbus.schedule.entity.ScheduleRouteEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRouteEntityRepository extends JpaRepository<ScheduleRouteEntity, String> {

  @EntityGraph(attributePaths = {"fromLocation", "toLocation"})
  List<ScheduleRouteEntity> findAllByOrderByRouteCodeAsc();

  @EntityGraph(attributePaths = {"fromLocation", "toLocation"})
  Optional<ScheduleRouteEntity> findByRouteCode(String routeCode);

  @EntityGraph(attributePaths = {"fromLocation", "toLocation"})
  @Query("""
      select route
      from ScheduleRouteEntity route
      where lower(route.fromLocation.name) = lower(:fromStop)
        and lower(route.toLocation.name) = lower(:toStop)
      order by route.routeCode
      """)
  List<ScheduleRouteEntity> findAllByStops(@Param("fromStop") String fromStop, @Param("toStop") String toStop);

  long countByFromLocation_IdOrToLocation_Id(Long fromLocationId, Long toLocationId);
}
