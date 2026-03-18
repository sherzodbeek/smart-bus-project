package com.smartbus.gateway.repository;

import com.smartbus.gateway.entity.GatewayBusEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayBusEntityRepository extends JpaRepository<GatewayBusEntity, Long> {

  List<GatewayBusEntity> findAllByOrderByBusIdAsc();

  long countByStatusIgnoreCase(String status);

  List<GatewayBusEntity> findTop5ByStatusNotIgnoreCaseOrderByUpdatedAtDesc(String status);
}
