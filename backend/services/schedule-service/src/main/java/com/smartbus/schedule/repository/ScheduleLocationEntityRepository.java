package com.smartbus.schedule.repository;

import com.smartbus.schedule.entity.ScheduleLocationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleLocationEntityRepository extends JpaRepository<ScheduleLocationEntity, Long> {

  Optional<ScheduleLocationEntity> findByNameIgnoreCase(String name);

  List<ScheduleLocationEntity> findAllByOrderByNameAsc();

  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
