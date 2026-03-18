package com.smartbus.gateway.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrap implements ApplicationRunner {

  private final AuthService authService;
  private final AuthProperties authProperties;

  public AuthBootstrap(AuthService authService, AuthProperties authProperties) {
    this.authService = authService;
    this.authProperties = authProperties;
  }

  @Override
  public void run(ApplicationArguments args) {
    authService.ensureAdminUser(
        authProperties.adminName(),
        authProperties.adminEmail(),
        authProperties.adminPassword()
    );
  }
}
