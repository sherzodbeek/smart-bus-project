package com.smartbus.booking.repository;

import com.smartbus.booking.entity.BookingProcessEntity;
import com.smartbus.booking.model.BookingLifecycleState;
import com.smartbus.booking.model.BookingProcessInstance;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaBookingProcessRepository implements BookingProcessRepository {

  private final BookingProcessEntityRepository entityRepository;

  public JpaBookingProcessRepository(BookingProcessEntityRepository entityRepository) {
    this.entityRepository = entityRepository;
  }

  @Override
  public void create(BookingProcessInstance processInstance) {
    entityRepository.save(toEntity(processInstance));
  }

  @Override
  public void updateState(String bookingReference, BookingLifecycleState state, String lastError) {
    BookingProcessEntity entity = requireEntity(bookingReference);
    entity.setCurrentState(state);
    entity.setLastError(lastError);
    entityRepository.save(entity);
  }

  @Override
  public void enrichBooking(
      String bookingReference,
      String routeCode,
      String departureTime,
      String arrivalTime,
      double totalAmount,
      String paymentTransactionId,
      String notificationId
  ) {
    BookingProcessEntity entity = requireEntity(bookingReference);
    entity.setRouteCode(routeCode);
    entity.setDepartureTime(departureTime);
    entity.setArrivalTime(arrivalTime);
    entity.setTotalAmount(totalAmount);
    entity.setPaymentTransactionId(paymentTransactionId);
    entity.setNotificationId(notificationId);
    entityRepository.save(entity);
  }

  @Override
  public Optional<BookingProcessInstance> findByBookingReference(String bookingReference) {
    return entityRepository.findById(bookingReference).map(this::toModel);
  }

  @Override
  public List<BookingProcessInstance> findByCustomerEmail(String customerEmail) {
    return entityRepository.findByCustomerEmailIgnoreCaseOrderByUpdatedAtDesc(customerEmail)
        .stream()
        .map(this::toModel)
        .toList();
  }

  @Override
  public List<BookingProcessInstance> findAll() {
    return entityRepository.findAllByOrderByUpdatedAtDesc()
        .stream()
        .map(this::toModel)
        .toList();
  }

  private BookingProcessEntity requireEntity(String bookingReference) {
    return entityRepository.findById(bookingReference)
        .orElseThrow(() -> new IllegalStateException("Booking process was not found: " + bookingReference));
  }

  private BookingProcessEntity toEntity(BookingProcessInstance instance) {
    BookingProcessEntity entity = new BookingProcessEntity();
    entity.setBookingReference(instance.bookingReference());
    entity.setCustomerName(instance.customerName());
    entity.setCustomerEmail(instance.customerEmail());
    entity.setFromStop(instance.fromStop());
    entity.setToStop(instance.toStop());
    entity.setTripDate(instance.tripDate());
    entity.setTripType(instance.tripType());
    entity.setPassengers(instance.passengers());
    entity.setRouteCode(instance.routeCode());
    entity.setDepartureTime(instance.departureTime());
    entity.setArrivalTime(instance.arrivalTime());
    entity.setTotalAmount(instance.totalAmount());
    entity.setPaymentTransactionId(instance.paymentTransactionId());
    entity.setNotificationId(instance.notificationId());
    entity.setCurrentState(instance.currentState());
    entity.setLastError(instance.lastError());
    entity.setCreatedAt(instance.createdAt());
    entity.setUpdatedAt(instance.updatedAt());
    return entity;
  }

  private BookingProcessInstance toModel(BookingProcessEntity entity) {
    return new BookingProcessInstance(
        entity.getBookingReference(),
        entity.getCustomerName(),
        entity.getCustomerEmail(),
        entity.getFromStop(),
        entity.getToStop(),
        entity.getTripDate(),
        entity.getTripType(),
        entity.getPassengers(),
        entity.getRouteCode(),
        entity.getDepartureTime(),
        entity.getArrivalTime(),
        entity.getTotalAmount() == null ? 0.0 : entity.getTotalAmount(),
        entity.getPaymentTransactionId(),
        entity.getNotificationId(),
        entity.getCurrentState(),
        entity.getLastError(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }
}
