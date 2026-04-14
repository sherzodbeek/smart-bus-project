package com.smartbus.payment.repository;

import com.smartbus.payment.model.PaymentRecord;
import java.util.List;
import java.util.Optional;

public interface PaymentRecordRepository {

  void save(PaymentRecord record);

  Optional<PaymentRecord> findByTransactionId(String transactionId);

  List<PaymentRecord> findByBookingReference(String bookingReference);

  List<PaymentRecord> findAll();

  boolean deleteByTransactionId(String transactionId);

  Optional<PaymentRecord> updateStatus(String transactionId, String status);
}
