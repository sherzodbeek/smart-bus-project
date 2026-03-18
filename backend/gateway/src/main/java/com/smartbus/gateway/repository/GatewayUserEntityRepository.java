package com.smartbus.gateway.repository;

import com.smartbus.gateway.entity.GatewayUserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayUserEntityRepository extends JpaRepository<GatewayUserEntity, Long> {

  Optional<GatewayUserEntity> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

  List<GatewayUserEntity> findAllByOrderByCreatedAtDesc();
}
