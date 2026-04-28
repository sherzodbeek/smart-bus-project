package com.smartbus.gateway.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers("/api/v1/system/services").permitAll()
            .requestMatchers("/api/v1/frontend/routes", "/api/v1/frontend/quote", "/api/v1/frontend/contact").permitAll()
            .requestMatchers("/api/v1/frontend/admin/**").hasRole("ADMIN")
            .requestMatchers(
                "/api/v1/frontend/bookings/**", "/api/v1/frontend/bookings",
                "/api/v1/frontend/profile/**", "/api/v1/frontend/profile",
                "/api/v1/frontend/ticket-documents",
                "/api/v1/frontend/recommendations",
                "/api/v1/frontend/semantic/**",
                "/api/v1/frontend/intelligent/**",
                "/api/v1/gateway/**"
            ).authenticated()
            .anyRequest().denyAll()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(GatewayUserRepository gatewayUserRepository) {
    return username -> gatewayUserRepository.findByEmail(username.toLowerCase())
        .map(user -> User.withUsername(user.email())
            .password(user.passwordHash())
            .roles(user.role())
            .build())
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
  }
}
