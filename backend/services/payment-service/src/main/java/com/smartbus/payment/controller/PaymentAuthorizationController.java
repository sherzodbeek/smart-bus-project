package com.smartbus.payment.controller;

import com.smartbus.payment.dto.ApiErrorResponse;
import com.smartbus.payment.dto.PaymentAuthorizationRequest;
import com.smartbus.payment.dto.PaymentAuthorizationResponse;
import com.smartbus.payment.dto.PaymentRecordResponse;
import com.smartbus.payment.dto.PaymentStatusUpdateRequest;
import com.smartbus.payment.model.PaymentRecord;
import com.smartbus.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentAuthorizationController {

  private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationController.class);

  private final PaymentService paymentService;

  public PaymentAuthorizationController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/authorize")
  public ResponseEntity<PaymentAuthorizationResponse> authorize(
      @Valid @RequestBody PaymentAuthorizationRequest request
  ) {
    log.info(
        "paymentAuthorizeRequest bookingReference={} customerEmail={} amount={}",
        request.bookingReference(),
        request.customerEmail(),
        request.amount()
    );
    PaymentAuthorizationResponse response = paymentService.authorize(request);
    URI location = URI.create("/api/v1/payments/records/" + response.transactionId());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/records/{transactionId}")
  public ResponseEntity<?> getRecord(
      @PathVariable String transactionId,
      HttpServletRequest request
  ) {
    log.info("paymentRecordRequest transactionId={}", transactionId);
    return paymentService.findByTransactionId(transactionId)
        .<ResponseEntity<?>>map(record -> ResponseEntity.ok(toResponse(record)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "PAYMENT_RECORD_NOT_FOUND",
            "Payment record not found.",
            List.of("transactionId: " + transactionId),
            request.getRequestURI()
        )));
  }

  @PatchMapping("/records/{transactionId}")
  public ResponseEntity<?> updateStatus(
      @PathVariable String transactionId,
      @Valid @RequestBody PaymentStatusUpdateRequest body,
      HttpServletRequest request
  ) {
    log.info("paymentRecordStatusUpdateRequest transactionId={} status={}", transactionId, body.status());
    return paymentService.updateStatus(transactionId, body.status())
        .<ResponseEntity<?>>map(record -> ResponseEntity.ok(toResponse(record)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "PAYMENT_RECORD_NOT_FOUND",
            "Payment record not found.",
            List.of("transactionId: " + transactionId),
            request.getRequestURI()
        )));
  }

  @GetMapping("/records")
  public List<PaymentRecordResponse> listRecords(
      @RequestParam(required = false) String bookingReference
  ) {
    if (bookingReference != null && !bookingReference.isBlank()) {
      log.info("paymentRecordListRequest bookingReference={}", bookingReference);
      return paymentService.findByBookingReference(bookingReference).stream()
          .map(this::toResponse)
          .toList();
    }
    log.info("paymentRecordListAllRequest");
    return paymentService.findAll().stream()
        .map(this::toResponse)
        .toList();
  }

  @DeleteMapping("/records/{transactionId}")
  public ResponseEntity<ApiErrorResponse> deleteRecord(
      @PathVariable String transactionId,
      HttpServletRequest request
  ) {
    log.info("paymentRecordDeleteRequest transactionId={}", transactionId);
    boolean deleted = paymentService.deleteByTransactionId(transactionId);
    if (!deleted) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
          HttpStatus.NOT_FOUND.value(),
          "PAYMENT_RECORD_NOT_FOUND",
          "Payment record not found.",
          List.of("transactionId: " + transactionId),
          request.getRequestURI()
      ));
    }
    return ResponseEntity.noContent().build();
  }

  private PaymentRecordResponse toResponse(PaymentRecord record) {
    return new PaymentRecordResponse(
        record.transactionId(),
        record.bookingReference(),
        record.customerEmail(),
        record.amount(),
        record.status(),
        record.reason(),
        record.createdAt()
    );
  }
}
