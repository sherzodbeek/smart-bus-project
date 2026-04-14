package com.smartbus.booking.service;

import com.smartbus.booking.dto.PagedResponse;
import com.smartbus.booking.exception.BookingNotFoundException;
import com.smartbus.booking.model.BookingProcessInstance;
import com.smartbus.booking.repository.BookingProcessRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BookingQueryService {

  private final BookingProcessRepository bookingProcessRepository;

  public BookingQueryService(BookingProcessRepository bookingProcessRepository) {
    this.bookingProcessRepository = bookingProcessRepository;
  }

  public BookingProcessInstance requireByBookingReference(String bookingReference) {
    return bookingProcessRepository.findByBookingReference(bookingReference)
        .orElseThrow(() -> new BookingNotFoundException(bookingReference));
  }

  public List<BookingProcessInstance> findByCustomerEmail(String customerEmail) {
    return bookingProcessRepository.findByCustomerEmail(customerEmail);
  }

  public List<BookingProcessInstance> findAll() {
    return bookingProcessRepository.findAll();
  }

  public PagedResponse<BookingProcessInstance> findAllPaged(int page, int size) {
    return bookingProcessRepository.findAllPaged(page, size);
  }

  public boolean cancel(String bookingReference) {
    return bookingProcessRepository.cancel(bookingReference);
  }
}
