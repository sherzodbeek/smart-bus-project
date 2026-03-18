package com.smartbus.booking.service;

import com.smartbus.booking.dto.BookingRequest;
import com.smartbus.booking.dto.BookingResponse;
import com.smartbus.booking.dto.NotificationDispatchRequest;
import com.smartbus.booking.dto.NotificationDispatchResponse;
import com.smartbus.booking.dto.NotificationPreparationRequest;
import com.smartbus.booking.dto.NotificationPreparationResponse;
import com.smartbus.booking.dto.PaymentAuthorizationRequest;
import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import com.smartbus.booking.dto.ScheduleQuoteRequest;
import com.smartbus.booking.dto.ScheduleQuoteResponse;
import com.smartbus.booking.dto.WorkflowLogEntry;
import com.smartbus.booking.exception.OrchestrationWorkflowException;
import com.smartbus.booking.messaging.BookingConfirmedEvent;
import com.smartbus.booking.messaging.BookingEventProducer;
import com.smartbus.booking.model.BookingLifecycleState;
import com.smartbus.booking.model.BookingProcessInstance;
import com.smartbus.booking.repository.BookingProcessRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class BookingWorkflowOperations {

  private static final Logger log = LoggerFactory.getLogger(BookingWorkflowOperations.class);

  private final BookingPartnerGateway partnerGateway;
  private final BookingProcessRepository bookingProcessRepository;
  private final BookingEventProducer bookingEventProducer;
  private final PartnerCallExecutor partnerCallExecutor;
  private final BookingWorkflowTraceStore traceStore;

  public BookingWorkflowOperations(
      BookingPartnerGateway partnerGateway,
      BookingProcessRepository bookingProcessRepository,
      BookingEventProducer bookingEventProducer,
      PartnerCallExecutor partnerCallExecutor,
      BookingWorkflowTraceStore traceStore
  ) {
    this.partnerGateway = partnerGateway;
    this.bookingProcessRepository = bookingProcessRepository;
    this.bookingEventProducer = bookingEventProducer;
    this.partnerCallExecutor = partnerCallExecutor;
    this.traceStore = traceStore;
  }

  public void initializeBooking(BookingRequest request, String bookingReference) {
    withBookingReference(bookingReference, () -> {
      bookingProcessRepository.create(new BookingProcessInstance(
          bookingReference,
          request.customerName(),
          request.customerEmail(),
          request.fromStop(),
          request.toStop(),
          request.tripDate(),
          request.tripType(),
          request.passengers(),
          null,
          null,
          null,
          0.0,
          null,
          null,
          BookingLifecycleState.RECEIVED,
          null,
          OffsetDateTime.now(),
          OffsetDateTime.now()
      ));
      recordStep(bookingReference, "receive-booking-request", "receive", "Received booking request");
    });
  }

  public ScheduleQuoteResponse validateSchedule(BookingRequest request, String bookingReference) {
    return withBookingReference(bookingReference, () -> {
      recordStep(bookingReference, "invoke-schedule-service", "invoke", "Validating outbound trip with schedule-service");
      ScheduleQuoteResponse quote = partnerCallExecutor.execute(
          bookingReference,
          "schedule-service",
          "quote-trip",
          () -> partnerGateway.quoteTrip(new ScheduleQuoteRequest(
              request.fromStop(),
              request.toStop(),
              request.tripDate(),
              request.tripType(),
              request.passengers()
          ))
      );
      bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.SCHEDULE_VALIDATED, null);

      recordStep(bookingReference, "switch-trip-type", "switch", "Trip type is " + request.tripType());
      if (!quote.tripAvailable()) {
        throw new OrchestrationWorkflowException("Outbound trip is not available for booking");
      }
      if ("round-trip".equalsIgnoreCase(request.tripType()) && !quote.returnTripAvailable()) {
        throw new OrchestrationWorkflowException("Return trip is not available for booking");
      }
      if ("round-trip".equalsIgnoreCase(request.tripType())) {
        bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.ROUND_TRIP_VALIDATED, null);
      }
      return quote;
    });
  }

  public void recordParallelFlowStart(String bookingReference) {
    withBookingReference(bookingReference, () -> {
      bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.PAYMENT_PENDING, null);
      recordStep(
          bookingReference,
          "parallel-partner-flow",
          "flow",
          "Starting payment authorization and notification preparation in parallel"
      );
    });
  }

  public PaymentAuthorizationResponse authorizePayment(BookingRequest request, String bookingReference, double totalAmount) {
    return withBookingReference(bookingReference, () -> {
      recordStep(bookingReference, "invoke-payment-service", "invoke", "Authorizing payment with payment-service");
      return partnerCallExecutor.execute(
          bookingReference,
          "payment-service",
          "authorize-payment",
          () -> partnerGateway.authorizePayment(new PaymentAuthorizationRequest(
              bookingReference,
              request.customerEmail(),
              totalAmount,
              request.paymentMethodToken()
          ))
      );
    });
  }

  public NotificationPreparationResponse prepareNotification(BookingRequest request, String bookingReference, String routeCode) {
    return withBookingReference(bookingReference, () -> {
      recordStep(
          bookingReference,
          "invoke-notification-prepare",
          "invoke",
          "Preparing confirmation message with notification-service"
      );
      return partnerCallExecutor.execute(
          bookingReference,
          "notification-service",
          "prepare-notification",
          () -> partnerGateway.prepareNotification(new NotificationPreparationRequest(
              bookingReference,
              request.customerName(),
              request.customerEmail(),
              routeCode
          ))
      );
    });
  }

  public void checkPaymentDecision(String bookingReference, PaymentAuthorizationResponse payment) {
    withBookingReference(bookingReference, () -> {
      recordStep(bookingReference, "switch-payment-result", "switch", "Payment status is " + payment.status());
      if (!payment.approved()) {
        throw new OrchestrationWorkflowException("Payment authorization failed: " + payment.reason());
      }
      bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.PAYMENT_AUTHORIZED, null);
      bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.NOTIFICATION_PENDING, null);
    });
  }

  public NotificationDispatchResponse dispatchNotification(
      String bookingReference,
      String customerEmail,
      String previewMessage,
      String paymentTransactionId
  ) {
    return withBookingReference(bookingReference, () -> {
      recordStep(
          bookingReference,
          "invoke-notification-dispatch",
          "invoke",
          "Dispatching confirmation through notification-service"
      );
      return partnerCallExecutor.execute(
          bookingReference,
          "notification-service",
          "dispatch-notification",
          () -> partnerGateway.dispatchNotification(new NotificationDispatchRequest(
              bookingReference,
              customerEmail,
              previewMessage + ". Payment " + paymentTransactionId + " authorized."
          ))
      );
    });
  }

  public void finalizeBooking(
      BookingRequest request,
      String bookingReference,
      ScheduleQuoteResponse quote,
      double totalAmount,
      PaymentAuthorizationResponse payment,
      NotificationDispatchResponse notification
  ) {
    withBookingReference(bookingReference, () -> {
      recordStep(
          bookingReference,
          "invoke-kafka-producer",
          "invoke",
          "Publishing booking confirmation event to Kafka"
      );
      try {
        bookingEventProducer.publish(new BookingConfirmedEvent(
            "booking-confirmed.v1",
            bookingReference,
            request.customerName(),
            request.customerEmail(),
            quote.routeCode(),
            request.tripDate(),
            request.tripType(),
            request.passengers(),
            totalAmount,
            payment.transactionId(),
            "CONFIRMED"
        ));
      } catch (RuntimeException exception) {
        log.warn(
            "bookingEventPublishFailed bookingReference={} exceptionType={} message={}",
            bookingReference,
            exception.getClass().getSimpleName(),
            exception.getMessage()
        );
      }
      bookingProcessRepository.enrichBooking(
          bookingReference,
          quote.routeCode(),
          quote.departureTime(),
          quote.arrivalTime(),
          totalAmount,
          payment.transactionId(),
          notification.notificationId()
      );
      bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.CONFIRMED, null);
      recordStep(
          bookingReference,
          "reply-booking-response",
          "reply",
          "Booking completed and response returned to caller"
      );
    });
  }

  public void handleFailure(String bookingReference, Throwable throwable) {
    withBookingReference(bookingReference, () -> {
      bookingProcessRepository.updateState(bookingReference, BookingLifecycleState.FAILED, throwable.getMessage());
      log.error(
          "bookingWorkflowFailed bookingReference={} exceptionType={} message={}",
          bookingReference,
          throwable.getClass().getSimpleName(),
          throwable.getMessage()
      );
    });
  }

  public BookingResponse buildResponse(String bookingReference) {
    BookingProcessInstance instance = bookingProcessRepository.findByBookingReference(bookingReference)
        .orElseThrow(() -> new OrchestrationWorkflowException("Booking process was not found"));
    return new BookingResponse(
        instance.bookingReference(),
        instance.bookingReference(),
        instance.currentState().name(),
        instance.routeCode(),
        instance.paymentTransactionId(),
        instance.notificationId(),
        instance.totalAmount(),
        traceStore.snapshot(bookingReference)
    );
  }

  private void recordStep(String bookingReference, String step, String element, String detail) {
    WorkflowLogEntry entry = new WorkflowLogEntry(step, element, bookingReference + " " + detail);
    traceStore.append(bookingReference, entry);
    log.info("workflowStep bookingReference={} step={} element={} detail={}", bookingReference, step, element, detail);
  }

  private void withBookingReference(String bookingReference, Runnable runnable) {
    try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingReference", bookingReference)) {
      runnable.run();
    }
  }

  private <T> T withBookingReference(String bookingReference, java.util.function.Supplier<T> supplier) {
    try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingReference", bookingReference)) {
      return supplier.get();
    }
  }
}
