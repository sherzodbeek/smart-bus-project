package com.smartbus.gateway.auth;

import java.util.Optional;

public interface GatewayUserRepository {

  Optional<GatewayUser> findByEmail(String email);

  GatewayUser create(String fullName, String email, String passwordHash, String role);
}
