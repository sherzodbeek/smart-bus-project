package com.smartbus.gateway.repository;

import com.smartbus.gateway.entity.GatewaySettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewaySettingEntityRepository extends JpaRepository<GatewaySettingEntity, String> {
}
