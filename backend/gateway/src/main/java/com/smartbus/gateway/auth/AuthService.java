package com.smartbus.gateway.auth;

import com.smartbus.gateway.exception.GatewayApiException;
import com.smartbus.gateway.util.HtmlSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final GatewayUserRepository gatewayUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      GatewayUserRepository gatewayUserRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService
  ) {
    this.gatewayUserRepository = gatewayUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public AuthResponse register(RegisterRequest request) {
    log.info("authRegisterStart email={}", request.email());
    gatewayUserRepository.findByEmail(request.email()).ifPresent(existing -> {
      throw new GatewayApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
    });

    GatewayUser user = gatewayUserRepository.create(
        HtmlSanitizer.strip(request.fullName()),
        request.email().trim().toLowerCase(),
        passwordEncoder.encode(request.password()),
        "USER"
    );
    log.info("authRegisterSuccess email={} role={}", user.email(), user.role());
    return toResponse(user);
  }

  public AuthResponse login(LoginRequest request) {
    log.info("authLoginStart email={}", request.email());
    GatewayUser user = gatewayUserRepository.findByEmail(request.email().trim().toLowerCase())
        .orElseThrow(() -> new GatewayApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new GatewayApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    log.info("authLoginSuccess email={} role={}", user.email(), user.role());
    return toResponse(user);
  }

  public void ensureAdminUser(String fullName, String email, String password) {
    if (gatewayUserRepository.findByEmail(email).isPresent()) {
      return;
    }
    gatewayUserRepository.create(fullName, email.toLowerCase(), passwordEncoder.encode(password), "ADMIN");
    log.info("authEnsureAdminCreated email={} role=ADMIN", email.toLowerCase());
  }

  private AuthResponse toResponse(GatewayUser user) {
    return new AuthResponse(
        jwtService.issueToken(user),
        user.fullName(),
        user.email(),
        user.role().toLowerCase()
    );
  }
}
