package com.smartbus.payment.service;

import com.smartbus.payment.dto.PaymentAuthorizationRequest;
import com.smartbus.payment.dto.PaymentAuthorizationResponse;
import com.smartbus.payment.messaging.PaymentDeclinedEvent;
import com.smartbus.payment.messaging.PaymentDeclinedEventProducer;
import com.smartbus.payment.model.PaymentRecord;
import com.smartbus.payment.repository.PaymentRecordRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentRecordRepository paymentRecordRepository;
  private final PaymentDeclinedEventProducer declinedEventProducer;

  public PaymentService(
      PaymentRecordRepository paymentRecordRepository,
      PaymentDeclinedEventProducer declinedEventProducer
  ) {
    this.paymentRecordRepository = paymentRecordRepository;
    this.declinedEventProducer = declinedEventProducer;
  }

  public PaymentAuthorizationResponse authorize(PaymentAuthorizationRequest request) {
    boolean approved = request.amount() <= 150.00
        && request.paymentMethodToken() != null
        && !request.paymentMethodToken().isBlank();

    String transactionId = approved
        ? "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        : "PAY-DECLINED";
    String status = approved ? "AUTHORIZED" : "DECLINED";
    String reason = approved ? "Authorization approved" : "Payment rule rejected the request";

    paymentRecordRepository.save(new PaymentRecord(
        transactionId,
        request.bookingReference(),
        request.customerEmail(),
        request.amount(),
        status,
        reason,
        OffsetDateTime.now()
    ));

    log.info(
        "paymentAuthorizeDecision bookingReference={} transactionId={} status={}",
        request.bookingReference(),
        transactionId,
        status
    );

    if (!approved) {
      declinedEventProducer.publish(new PaymentDeclinedEvent(
          "payment-declined.v1",
          transactionId,
          request.bookingReference(),
          request.customerEmail(),
          request.amount(),
          reason
      ));
    }

    return new PaymentAuthorizationResponse(approved, transactionId, status, reason);
  }

  public Optional<PaymentRecord> findByTransactionId(String transactionId) {
    return paymentRecordRepository.findByTransactionId(transactionId);
  }

  public List<PaymentRecord> findByBookingReference(String bookingReference) {
    return paymentRecordRepository.findByBookingReference(bookingReference);
  }

  public List<PaymentRecord> findAll() {
    return paymentRecordRepository.findAll();
  }

  public boolean deleteByTransactionId(String transactionId) {
    return paymentRecordRepository.deleteByTransactionId(transactionId);
  }

  public Optional<PaymentRecord> updateStatus(String transactionId, String status) {
    return paymentRecordRepository.updateStatus(transactionId, status);
  }
}
