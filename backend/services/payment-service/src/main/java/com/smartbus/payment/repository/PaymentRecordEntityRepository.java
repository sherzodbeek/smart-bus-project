package com.smartbus.payment.repository;

import com.smartbus.payment.entity.PaymentRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRecordEntityRepository extends JpaRepository<PaymentRecordEntity, String> {

  List<PaymentRecordEntity> findByBookingReferenceOrderByCreatedAtDesc(String bookingReference);

  List<PaymentRecordEntity> findAllByOrderByCreatedAtDesc();
}
