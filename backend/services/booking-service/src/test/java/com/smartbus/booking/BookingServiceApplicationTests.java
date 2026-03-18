package com.smartbus.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BookingServiceApplicationTests {

  @Test
  void moduleNameMatchesExpectation() {
    assertEquals("com.smartbus.booking", BookingServiceApplication.class.getPackageName());
  }
}
