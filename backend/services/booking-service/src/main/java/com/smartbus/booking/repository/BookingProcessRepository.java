package com.smartbus.booking.repository;

import com.smartbus.booking.model.BookingLifecycleState;
import com.smartbus.booking.model.BookingProcessInstance;
import java.util.List;
import java.util.Optional;

public interface BookingProcessRepository {

  void create(BookingProcessInstance processInstance);

  void updateState(String bookingReference, BookingLifecycleState state, String lastError);

  void enrichBooking(
      String bookingReference,
      String routeCode,
      String departureTime,
      String arrivalTime,
      double totalAmount,
      String paymentTransactionId,
      String notificationId
  );

  Optional<BookingProcessInstance> findByBookingReference(String bookingReference);

  List<BookingProcessInstance> findByCustomerEmail(String customerEmail);

  List<BookingProcessInstance> findAll();
}
