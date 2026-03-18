package com.smartbus.gateway.auth;

import com.smartbus.gateway.entity.GatewayUserEntity;
import com.smartbus.gateway.repository.GatewayUserEntityRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaGatewayUserRepository implements GatewayUserRepository {

  private final GatewayUserEntityRepository entityRepository;

  public JpaGatewayUserRepository(GatewayUserEntityRepository entityRepository) {
    this.entityRepository = entityRepository;
  }

  @Override
  public Optional<GatewayUser> findByEmail(String email) {
    return entityRepository.findByEmailIgnoreCase(email).map(this::toModel);
  }

  @Override
  public GatewayUser create(String fullName, String email, String passwordHash, String role) {
    GatewayUserEntity entity = new GatewayUserEntity();
    entity.setFullName(fullName);
    entity.setEmail(email.toLowerCase());
    entity.setPasswordHash(passwordHash);
    entity.setRole(role);
    GatewayUserEntity saved = entityRepository.save(entity);
    return toModel(saved);
  }

  private GatewayUser toModel(GatewayUserEntity entity) {
    return new GatewayUser(
        entity.getId(),
        entity.getFullName(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getRole()
    );
  }
}
