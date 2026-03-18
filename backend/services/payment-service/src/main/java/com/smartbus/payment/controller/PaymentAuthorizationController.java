package com.smartbus.payment.controller;

import com.smartbus.payment.dto.PaymentAuthorizationRequest;
import com.smartbus.payment.dto.PaymentAuthorizationResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentAuthorizationController {

  private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationController.class);

  @PostMapping("/authorize")
  public PaymentAuthorizationResponse authorize(@Valid @RequestBody PaymentAuthorizationRequest request) {
    log.info(
        "paymentAuthorizeRequest bookingReference={} customerEmail={} amount={} tokenPresent={}",
        request.bookingReference(),
        request.customerEmail(),
        request.amount(),
        request.paymentMethodToken() != null && !request.paymentMethodToken().isBlank()
    );
    boolean approved = request.amount() <= 150.00 && request.paymentMethodToken() != null && !request.paymentMethodToken().isBlank();
    log.info(
        "paymentAuthorizeDecision bookingReference={} approved={} reason={}",
        request.bookingReference(),
        approved,
        approved ? "Authorization approved" : "Payment rule rejected the request"
    );
    return new PaymentAuthorizationResponse(
        approved,
        approved ? "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : "PAY-DECLINED",
        approved ? "AUTHORIZED" : "DECLINED",
        approved ? "Authorization approved" : "Payment rule rejected the request"
    );
  }
}
