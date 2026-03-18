package com.smartbus.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NotificationServiceApplicationTests {

  @Test
  void moduleNameMatchesExpectation() {
    assertEquals("com.smartbus.notification", NotificationServiceApplication.class.getPackageName());
  }
}
