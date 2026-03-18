package com.smartbus.booking.controller;

import com.smartbus.booking.dto.BookingProcessStateResponse;
import com.smartbus.booking.dto.BookingRequest;
import com.smartbus.booking.dto.BookingResponse;
import com.smartbus.booking.dto.BookingSummaryResponse;
import com.smartbus.booking.exception.OrchestrationWorkflowException;
import com.smartbus.booking.model.BookingProcessInstance;
import com.smartbus.booking.repository.BookingProcessRepository;
import com.smartbus.booking.service.BookingOrchestrationService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingOrchestrationController {

  private static final Logger log = LoggerFactory.getLogger(BookingOrchestrationController.class);

  private final BookingOrchestrationService orchestrationService;
  private final BookingProcessRepository bookingProcessRepository;

  public BookingOrchestrationController(
      BookingOrchestrationService orchestrationService,
      BookingProcessRepository bookingProcessRepository
  ) {
    this.orchestrationService = orchestrationService;
    this.bookingProcessRepository = bookingProcessRepository;
  }

  @PostMapping("/orchestrated-bookings")
  @ResponseStatus(HttpStatus.CREATED)
  public BookingResponse create(@Valid @RequestBody BookingRequest request) {
    log.info(
        "bookingCreateRequest customerEmail={} fromStop={} toStop={} tripDate={} tripType={} passengers={}",
        request.customerEmail(),
        request.fromStop(),
        request.toStop(),
        request.tripDate(),
        request.tripType(),
        request.passengers()
    );
    return orchestrationService.orchestrateBooking(request);
  }

  @GetMapping("/{bookingReference}/state")
  public BookingProcessStateResponse state(@PathVariable String bookingReference) {
    log.info("bookingStateRequest bookingReference={}", bookingReference);
    BookingProcessInstance instance = bookingProcessRepository.findByBookingReference(bookingReference)
        .orElseThrow(() -> new OrchestrationWorkflowException("Booking process was not found"));
    return new BookingProcessStateResponse(
        instance.bookingReference(),
        instance.bookingReference(),
        instance.currentState().name(),
        instance.lastError()
    );
  }

  @GetMapping
  public List<BookingSummaryResponse> listByCustomerEmail(@RequestParam String customerEmail) {
    log.info("bookingListByCustomerRequest customerEmail={}", customerEmail);
    return mapSummaries(bookingProcessRepository.findByCustomerEmail(customerEmail));
  }

  @GetMapping("/admin/bookings")
  public List<BookingSummaryResponse> listAll() {
    log.info("bookingAdminListRequest");
    return mapSummaries(bookingProcessRepository.findAll());
  }

  private List<BookingSummaryResponse> mapSummaries(List<BookingProcessInstance> instances) {
    return instances.stream()
        .map(instance -> new BookingSummaryResponse(
            instance.bookingReference(),
            instance.customerName(),
            instance.customerEmail(),
            instance.fromStop(),
            instance.toStop(),
            instance.tripDate(),
            instance.tripType(),
            instance.passengers(),
            instance.routeCode(),
            instance.departureTime(),
            instance.arrivalTime(),
            instance.totalAmount(),
            instance.paymentTransactionId(),
            instance.notificationId(),
            instance.currentState().name(),
            instance.lastError()
        ))
        .toList();
  }
}
