package com.smartbus.booking.service;

import com.smartbus.booking.dto.BookingRequest;
import com.smartbus.booking.dto.BookingResponse;
import com.smartbus.booking.util.HtmlSanitizer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BookingOrchestrationService {

  private static final Logger log = LoggerFactory.getLogger(BookingOrchestrationService.class);

  private final BookingWorkflowRunner workflowRunner;
  private final BookingWorkflowOperations workflowOperations;

  public BookingOrchestrationService(
      BookingWorkflowRunner workflowRunner,
      BookingWorkflowOperations workflowOperations
  ) {
    this.workflowRunner = workflowRunner;
    this.workflowOperations = workflowOperations;
  }

  public BookingResponse orchestrateBooking(BookingRequest request) {
    BookingRequest sanitized = new BookingRequest(
        HtmlSanitizer.strip(request.customerName()),
        request.customerEmail(),
        request.fromStop(),
        request.toStop(),
        request.tripDate(),
        request.tripType(),
        request.passengers(),
        request.paymentMethodToken()
    );
    String bookingReference = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    log.info("bookingOrchestrationStart bookingReference={} customerEmail={}", bookingReference, sanitized.customerEmail());
    workflowRunner.runBookingWorkflow(sanitized, bookingReference);
    log.info("bookingOrchestrationComplete bookingReference={}", bookingReference);
    return workflowOperations.buildResponse(bookingReference);
  }
}
