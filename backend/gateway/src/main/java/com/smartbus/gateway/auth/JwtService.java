package com.smartbus.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final AuthProperties authProperties;

  public JwtService(AuthProperties authProperties) {
    this.authProperties = authProperties;
  }

  public String issueToken(GatewayUser user) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(authProperties.jwtExpiration());

    return Jwts.builder()
        .subject(user.email())
        .claim("role", user.role())
        .claim("name", user.fullName())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey())
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(signingKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private SecretKey signingKey() {
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(authProperties.jwtSecret());
    } catch (RuntimeException exception) {
      keyBytes = authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8);
    }
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
