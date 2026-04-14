package com.smartbus.payment.repository;

import com.smartbus.payment.entity.PaymentRecordEntity;
import com.smartbus.payment.model.PaymentRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPaymentRecordRepository implements PaymentRecordRepository {

  private final PaymentRecordEntityRepository entityRepository;

  public JpaPaymentRecordRepository(PaymentRecordEntityRepository entityRepository) {
    this.entityRepository = entityRepository;
  }

  @Override
  public void save(PaymentRecord record) {
    entityRepository.save(toEntity(record));
  }

  @Override
  public Optional<PaymentRecord> findByTransactionId(String transactionId) {
    return entityRepository.findById(transactionId).map(this::toModel);
  }

  @Override
  public List<PaymentRecord> findByBookingReference(String bookingReference) {
    return entityRepository.findByBookingReferenceOrderByCreatedAtDesc(bookingReference)
        .stream()
        .map(this::toModel)
        .toList();
  }

  @Override
  public List<PaymentRecord> findAll() {
    return entityRepository.findAllByOrderByCreatedAtDesc()
        .stream()
        .map(this::toModel)
        .toList();
  }

  @Override
  public boolean deleteByTransactionId(String transactionId) {
    if (!entityRepository.existsById(transactionId)) {
      return false;
    }
    entityRepository.deleteById(transactionId);
    return true;
  }

  @Override
  public Optional<PaymentRecord> updateStatus(String transactionId, String status) {
    return entityRepository.findById(transactionId).map(entity -> {
      entity.setStatus(status);
      return toModel(entityRepository.save(entity));
    });
  }

  private PaymentRecordEntity toEntity(PaymentRecord record) {
    PaymentRecordEntity entity = new PaymentRecordEntity();
    entity.setTransactionId(record.transactionId());
    entity.setBookingReference(record.bookingReference());
    entity.setCustomerEmail(record.customerEmail());
    entity.setAmount(record.amount());
    entity.setStatus(record.status());
    entity.setReason(record.reason());
    entity.setCreatedAt(record.createdAt());
    return entity;
  }

  private PaymentRecord toModel(PaymentRecordEntity entity) {
    return new PaymentRecord(
        entity.getTransactionId(),
        entity.getBookingReference(),
        entity.getCustomerEmail(),
        entity.getAmount(),
        entity.getStatus(),
        entity.getReason(),
        entity.getCreatedAt()
    );
  }
}
