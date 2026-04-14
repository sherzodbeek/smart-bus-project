package com.smartbus.booking;

import com.smartbus.booking.config.BookingFaultHandlingProperties;
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
import com.smartbus.booking.exception.OrchestrationWorkflowException;
import com.smartbus.booking.exception.PartnerServiceException;
import com.smartbus.booking.messaging.BookingConfirmedEvent;
import com.smartbus.booking.messaging.BookingEventProducer;
import com.smartbus.booking.model.BookingLifecycleState;
import com.smartbus.booking.model.BookingProcessInstance;
import com.smartbus.booking.repository.BookingProcessRepository;
import com.smartbus.booking.service.BookingOrchestrationService;
import com.smartbus.booking.service.BookingPartnerGateway;
import com.smartbus.booking.service.BookingWorkflowOperations;
import com.smartbus.booking.service.BookingWorkflowTraceStore;
import com.smartbus.booking.service.LocalBookingWorkflowRunner;
import com.smartbus.booking.service.PartnerCallExecutor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class BookingOrchestrationServiceTests {

  private FakeBookingPartnerGateway partnerServiceClient;
  private InMemoryBookingProcessRepository bookingProcessRepository;
  private FakeBookingEventProducer bookingEventProducer;
  private BookingOrchestrationService service;

  @BeforeEach
  void setUp() {
    partnerServiceClient = new FakeBookingPartnerGateway();
    bookingProcessRepository = new InMemoryBookingProcessRepository();
    bookingEventProducer = new FakeBookingEventProducer();
    Executor sameThreadExecutor = Runnable::run;
    service = createService(
        new BookingFaultHandlingProperties(Duration.ofMillis(250), 3, Duration.ofMillis(10)),
        sameThreadExecutor,
        sameThreadExecutor
    );
  }

  @Test
  void orchestratesHappyPathWithWorkflowElements() {
    partnerServiceClient.quoteResponse = new ScheduleQuoteResponse(true, true, "SB-101", "08:00 AM", "09:15 AM", 12.50, 20);
    partnerServiceClient.paymentResponse = new PaymentAuthorizationResponse(true, "PAY-1001", "AUTHORIZED", "Authorization approved");
    partnerServiceClient.preparationResponse = new NotificationPreparationResponse("BOOKING-CONFIRMATION", "Preview ready", "EMAIL");
    partnerServiceClient.dispatchResponse = new NotificationDispatchResponse("NTF-1001", "SENT");

    BookingResponse response = service.orchestrateBooking(new BookingRequest(
        "Bekzod",
        "bekzod@example.com",
        "Downtown Terminal",
        "Airport Station",
        "2026-03-20",
        "one-way",
        2,
        "tok_visa"
    ));

    assertEquals("CONFIRMED", response.status());
    assertEquals("SB-101", response.routeCode());
    assertEquals("PAY-1001", response.paymentTransactionId());
    assertEquals("NTF-1001", response.notificationId());
    assertEquals(25.0, response.totalAmount());
    assertEquals(response.bookingReference(), response.correlationKey());
    assertEquals("receive", response.workflowLog().getFirst().element());
    assertEquals("reply", response.workflowLog().getLast().element());
    assertEquals("booking-confirmed.v1", bookingEventProducer.lastEvent.schemaVersion());
    assertEquals(response.bookingReference(), bookingEventProducer.lastEvent.bookingReference());
    assertEquals(
        BookingLifecycleState.CONFIRMED,
        bookingProcessRepository.findByBookingReference(response.bookingReference()).orElseThrow().currentState()
    );
  }

  @Test
  void rejectsWhenRoundTripReturnLegIsUnavailable() {
    partnerServiceClient.quoteResponse = new ScheduleQuoteResponse(true, false, "SB-101", "08:00 AM", "09:15 AM", 12.50, 20);

    OrchestrationWorkflowException exception = assertThrows(
        OrchestrationWorkflowException.class,
        () -> service.orchestrateBooking(new BookingRequest(
            "Bekzod",
            "bekzod@example.com",
            "Downtown Terminal",
            "Airport Station",
            "2026-03-20",
            "round-trip",
            1,
            "tok_visa"
        ))
    );

    assertEquals("Return trip is not available for booking", exception.getMessage());
  }

  @Test
  void retriesTransientPreparationFailureAndEventuallyConfirms() {
    partnerServiceClient.quoteResponse = new ScheduleQuoteResponse(true, true, "SB-101", "08:00 AM", "09:15 AM", 12.50, 20);
    partnerServiceClient.paymentResponse = new PaymentAuthorizationResponse(true, "PAY-1001", "AUTHORIZED", "Authorization approved");
    partnerServiceClient.dispatchResponse = new NotificationDispatchResponse("NTF-1001", "SENT");
    partnerServiceClient.preparationFailuresBeforeSuccess = 2;
    partnerServiceClient.preparationResponse = new NotificationPreparationResponse("BOOKING-CONFIRMATION", "Preview ready", "EMAIL");

    BookingResponse response = service.orchestrateBooking(new BookingRequest(
        "Bekzod",
        "bekzod@example.com",
        "Downtown Terminal",
        "Airport Station",
        "2026-03-20",
        "one-way",
        1,
        "tok_visa"
    ));

    assertEquals("CONFIRMED", response.status());
    assertEquals(3, partnerServiceClient.preparationAttempts.get());
  }

  @Test
  void timesOutSlowScheduleServiceAndMarksBookingFailed() {
    partnerServiceClient.quoteDelayMillis = 60;

    try (ExecutorService partnerExecutor = Executors.newFixedThreadPool(2)) {
      service = createService(
          new BookingFaultHandlingProperties(Duration.ofMillis(20), 2, Duration.ofMillis(5)),
          Runnable::run,
          partnerExecutor
      );

      PartnerServiceException exception = assertThrows(
          PartnerServiceException.class,
          () -> service.orchestrateBooking(new BookingRequest(
              "Bekzod",
              "bekzod@example.com",
              "Downtown Terminal",
              "Airport Station",
              "2026-03-20",
              "one-way",
              1,
              "tok_visa"
          ))
      );

      assertEquals("DOWNSTREAM_TIMEOUT", exception.errorCode());
      assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.status());
      assertEquals(2, exception.attempts());
      BookingProcessInstance failedInstance = bookingProcessRepository.instances.values().iterator().next();
      assertEquals(BookingLifecycleState.FAILED, failedInstance.currentState());
      assertTrue(failedInstance.lastError().contains("Please try again"));
    }
  }

  private BookingOrchestrationService createService(
      BookingFaultHandlingProperties properties,
      Executor orchestrationExecutor,
      Executor partnerExecutor
  ) {
    BookingWorkflowOperations operations = new BookingWorkflowOperations(
        partnerServiceClient,
        bookingProcessRepository,
        bookingEventProducer,
        new PartnerCallExecutor(properties, partnerExecutor),
        new BookingWorkflowTraceStore()
    );
    return new BookingOrchestrationService(
        new LocalBookingWorkflowRunner(operations, orchestrationExecutor),
        operations
    );
  }

  private static final class InMemoryBookingProcessRepository implements BookingProcessRepository {

    private final java.util.concurrent.ConcurrentHashMap<String, BookingProcessInstance> instances =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void create(BookingProcessInstance processInstance) {
      instances.put(processInstance.bookingReference(), processInstance);
    }

    @Override
    public void updateState(String bookingReference, BookingLifecycleState state, String lastError) {
      instances.computeIfPresent(bookingReference, (key, existing) -> new BookingProcessInstance(
          existing.bookingReference(),
          existing.customerName(),
          existing.customerEmail(),
          existing.fromStop(),
          existing.toStop(),
          existing.tripDate(),
          existing.tripType(),
          existing.passengers(),
          existing.routeCode(),
          existing.departureTime(),
          existing.arrivalTime(),
          existing.totalAmount(),
          existing.paymentTransactionId(),
          existing.notificationId(),
          state,
          lastError,
          existing.createdAt(),
          java.time.OffsetDateTime.now()
      ));
    }

    @Override
    public java.util.Optional<BookingProcessInstance> findByBookingReference(String bookingReference) {
      return java.util.Optional.ofNullable(instances.get(bookingReference));
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
      instances.computeIfPresent(bookingReference, (key, existing) -> new BookingProcessInstance(
          existing.bookingReference(),
          existing.customerName(),
          existing.customerEmail(),
          existing.fromStop(),
          existing.toStop(),
          existing.tripDate(),
          existing.tripType(),
          existing.passengers(),
          routeCode,
          departureTime,
          arrivalTime,
          totalAmount,
          paymentTransactionId,
          notificationId,
          existing.currentState(),
          existing.lastError(),
          existing.createdAt(),
          java.time.OffsetDateTime.now()
      ));
    }

    @Override
    public java.util.List<BookingProcessInstance> findByCustomerEmail(String customerEmail) {
      return instances.values().stream()
          .filter(instance -> instance.customerEmail().equalsIgnoreCase(customerEmail))
          .toList();
    }

    @Override
    public java.util.List<BookingProcessInstance> findAll() {
      return instances.values().stream().toList();
    }

    @Override
    public com.smartbus.booking.dto.PagedResponse<BookingProcessInstance> findAllPaged(int page, int size) {
      java.util.List<BookingProcessInstance> all = findAll();
      int fromIndex = page * size;
      int toIndex = Math.min(fromIndex + size, all.size());
      java.util.List<BookingProcessInstance> items = fromIndex >= all.size()
          ? java.util.List.of()
          : all.subList(fromIndex, toIndex);
      int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
      return new com.smartbus.booking.dto.PagedResponse<>(items, page, size, all.size(), totalPages);
    }

    @Override
    public boolean cancel(String bookingReference) {
      if (!instances.containsKey(bookingReference)) {
        return false;
      }
      updateState(bookingReference, com.smartbus.booking.model.BookingLifecycleState.CANCELLED, null);
      return true;
    }
  }

  private static final class FakeBookingPartnerGateway implements BookingPartnerGateway {

    private ScheduleQuoteResponse quoteResponse;
    private PaymentAuthorizationResponse paymentResponse;
    private NotificationPreparationResponse preparationResponse;
    private NotificationDispatchResponse dispatchResponse;
    private int preparationFailuresBeforeSuccess;
    private long quoteDelayMillis;
    private final AtomicInteger preparationAttempts = new AtomicInteger();

    @Override
    public ScheduleQuoteResponse quoteTrip(ScheduleQuoteRequest request) {
      sleep(quoteDelayMillis);
      return quoteResponse;
    }

    @Override
    public PaymentAuthorizationResponse authorizePayment(PaymentAuthorizationRequest request) {
      return paymentResponse;
    }

    @Override
    public NotificationPreparationResponse prepareNotification(NotificationPreparationRequest request) {
      int attempt = preparationAttempts.incrementAndGet();
      if (attempt <= preparationFailuresBeforeSuccess) {
        throw new ResourceAccessException("Temporary notification preparation outage");
      }
      return preparationResponse;
    }

    @Override
    public NotificationDispatchResponse dispatchNotification(NotificationDispatchRequest request) {
      return dispatchResponse;
    }

    private void sleep(long delayMillis) {
      if (delayMillis <= 0) {
        return;
      }
      try {
        Thread.sleep(delayMillis);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      }
    }
  }

  private static final class FakeBookingEventProducer extends BookingEventProducer {

    private BookingConfirmedEvent lastEvent;

    private FakeBookingEventProducer() {
      super(null, null);
    }

    @Override
    public void publish(BookingConfirmedEvent event) {
      this.lastEvent = event;
    }
  }
}
