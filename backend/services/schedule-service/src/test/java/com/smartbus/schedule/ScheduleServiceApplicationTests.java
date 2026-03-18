package com.smartbus.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScheduleServiceApplicationTests {

  @Test
  void moduleNameMatchesExpectation() {
    assertEquals("com.smartbus.schedule", ScheduleServiceApplication.class.getPackageName());
  }
}
