package com.smartbus.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GatewayApplicationTests {

  @Test
  void moduleNameMatchesExpectation() {
    assertEquals("com.smartbus.gateway", GatewayApplication.class.getPackageName());
  }
}
