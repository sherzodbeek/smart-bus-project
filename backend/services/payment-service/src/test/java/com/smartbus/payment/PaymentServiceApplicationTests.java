package com.smartbus.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaymentServiceApplicationTests {

  @Test
  void moduleNameMatchesExpectation() {
    assertEquals("com.smartbus.payment", PaymentServiceApplication.class.getPackageName());
  }
}
