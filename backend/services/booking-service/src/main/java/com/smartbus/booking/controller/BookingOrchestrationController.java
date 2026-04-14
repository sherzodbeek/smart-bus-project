package com.smartbus.booking.controller;

import com.smartbus.booking.dto.ApiErrorResponse;
import com.smartbus.booking.dto.BookingProcessStateResponse;
import com.smartbus.booking.dto.BookingRequest;
import com.smartbus.booking.dto.BookingResponse;
import com.smartbus.booking.dto.BookingSummaryResponse;
import com.smartbus.booking.dto.PagedResponse;
import com.smartbus.booking.model.BookingProcessInstance;
import com.smartbus.booking.service.BookingOrchestrationService;
import com.smartbus.booking.service.BookingQueryService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingOrchestrationController {

  private static final Logger log = LoggerFactory.getLogger(BookingOrchestrationController.class);

  private final BookingOrchestrationService orchestrationService;
  private final BookingQueryService bookingQueryService;

  public BookingOrchestrationController(
      BookingOrchestrationService orchestrationService,
      BookingQueryService bookingQueryService
  ) {
    this.orchestrationService = orchestrationService;
    this.bookingQueryService = bookingQueryService;
  }

  @PostMapping("/orchestrated-bookings")
  public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
    log.info(
        "bookingCreateRequest customerEmail={} fromStop={} toStop={} tripDate={} tripType={} passengers={}",
        request.customerEmail(),
        request.fromStop(),
        request.toStop(),
        request.tripDate(),
        request.tripType(),
        request.passengers()
    );
    BookingResponse response = orchestrationService.orchestrateBooking(request);
    URI location = URI.create("/api/v1/bookings/" + response.bookingReference());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/{bookingReference}")
  public BookingSummaryResponse get(@PathVariable String bookingReference) {
    log.info("bookingGetRequest bookingReference={}", bookingReference);
    return mapSummary(bookingQueryService.requireByBookingReference(bookingReference));
  }

  @GetMapping("/{bookingReference}/state")
  public BookingProcessStateResponse state(@PathVariable String bookingReference) {
    log.info("bookingStateRequest bookingReference={}", bookingReference);
    BookingProcessInstance instance = bookingQueryService.requireByBookingReference(bookingReference);
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
    return mapSummaries(bookingQueryService.findByCustomerEmail(customerEmail));
  }

  @GetMapping("/admin/bookings")
  public PagedResponse<BookingSummaryResponse> listAll(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    log.info("bookingAdminListRequest page={} size={}", page, size);
    PagedResponse<BookingProcessInstance> paged = bookingQueryService.findAllPaged(page, size);
    return new PagedResponse<>(
        mapSummaries(paged.items()),
        paged.page(),
        paged.size(),
        paged.totalElements(),
        paged.totalPages()
    );
  }

  @GetMapping(value = "/admin/bookings.csv", produces = "text/csv")
  public ResponseEntity<String> exportCsv() {
    log.info("bookingAdminCsvExportRequest");
    List<BookingProcessInstance> all = bookingQueryService.findAll();
    StringBuilder csv = new StringBuilder(
        "bookingReference,customerName,customerEmail,fromStop,toStop," +
        "tripDate,tripType,passengers,routeCode,departureTime,arrivalTime," +
        "totalAmount,paymentTransactionId,status\n"
    );
    for (BookingProcessInstance b : all) {
      csv.append(csvEscape(b.bookingReference())).append(',')
         .append(csvEscape(b.customerName())).append(',')
         .append(csvEscape(b.customerEmail())).append(',')
         .append(csvEscape(b.fromStop())).append(',')
         .append(csvEscape(b.toStop())).append(',')
         .append(csvEscape(b.tripDate())).append(',')
         .append(csvEscape(b.tripType())).append(',')
         .append(b.passengers()).append(',')
         .append(csvEscape(b.routeCode())).append(',')
         .append(csvEscape(b.departureTime())).append(',')
         .append(csvEscape(b.arrivalTime())).append(',')
         .append(b.totalAmount()).append(',')
         .append(csvEscape(b.paymentTransactionId())).append(',')
         .append(b.currentState().name())
         .append('\n');
    }
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookings.csv\"")
        .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
        .body(csv.toString());
  }

  private static String csvEscape(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  @DeleteMapping("/{bookingReference}")
  public ResponseEntity<ApiErrorResponse> cancel(
      @PathVariable String bookingReference,
      HttpServletRequest request
  ) {
    log.info("bookingCancelRequest bookingReference={}", bookingReference);
    boolean cancelled = bookingQueryService.cancel(bookingReference);
    if (!cancelled) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
          HttpStatus.NOT_FOUND.value(),
          "BOOKING_NOT_FOUND",
          "Booking not found.",
          List.of("bookingReference: " + bookingReference),
          request.getRequestURI()
      ));
    }
    return ResponseEntity.noContent().build();
  }

  private BookingSummaryResponse mapSummary(BookingProcessInstance instance) {
    return new BookingSummaryResponse(
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
    );
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
