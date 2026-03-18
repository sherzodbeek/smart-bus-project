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
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingCorrelationConcurrencyTests {

  @Test
  void keepsParallelBookingsIsolatedByCorrelationId() throws Exception {
    RecordingBookingProcessRepository repository = new RecordingBookingProcessRepository();
    RecordingBookingEventProducer eventProducer = new RecordingBookingEventProducer();
    DelayBookingPartnerGateway partnerGateway = new DelayBookingPartnerGateway();

    try (ExecutorService orchestrationExecutor = Executors.newFixedThreadPool(4);
         ExecutorService partnerExecutor = Executors.newFixedThreadPool(4);
         ExecutorService callers = Executors.newFixedThreadPool(6)) {
      BookingWorkflowOperations operations = new BookingWorkflowOperations(
          partnerGateway,
          repository,
          eventProducer,
          new PartnerCallExecutor(
              new BookingFaultHandlingProperties(Duration.ofMillis(250), 3, Duration.ofMillis(10)),
              partnerExecutor
          ),
          new BookingWorkflowTraceStore()
      );
      BookingOrchestrationService service = new BookingOrchestrationService(
          new LocalBookingWorkflowRunner(operations, orchestrationExecutor),
          operations
      );

      List<Future<BookingResponse>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        final int passengerIndex = i;
        futures.add(callers.submit(() -> service.orchestrateBooking(new BookingRequest(
            "Bekzod-" + passengerIndex,
            "bekzod" + passengerIndex + "@example.com",
            "Downtown Terminal",
            "Airport Station",
            "2026-03-25",
            passengerIndex % 2 == 0 ? "one-way" : "round-trip",
            1,
            "tok_" + passengerIndex
        ))));
      }

      List<BookingResponse> responses = new ArrayList<>();
      for (Future<BookingResponse> future : futures) {
        responses.add(future.get());
      }

      Set<String> bookingReferences = ConcurrentHashMap.newKeySet();
      for (BookingResponse response : responses) {
        bookingReferences.add(response.bookingReference());
        Optional<BookingProcessInstance> processInstance = repository.findByBookingReference(response.bookingReference());
        assertTrue(processInstance.isPresent());
        assertEquals(BookingLifecycleState.CONFIRMED, processInstance.orElseThrow().currentState());
        assertEquals(response.bookingReference(), response.correlationKey());
      }

      assertEquals(responses.size(), bookingReferences.size());
      assertEquals(responses.size(), eventProducer.events.size());
      assertEquals(responses.size(), repository.instances.size());
    }
  }

  private static final class DelayBookingPartnerGateway implements BookingPartnerGateway {

    @Override
    public ScheduleQuoteResponse quoteTrip(ScheduleQuoteRequest request) {
      sleep();
      return new ScheduleQuoteResponse(true, true, "SB-101", "08:00 AM", "09:15 AM", 12.50, 20);
    }

    @Override
    public PaymentAuthorizationResponse authorizePayment(PaymentAuthorizationRequest request) {
      sleep();
      return new PaymentAuthorizationResponse(true, "PAY-" + request.bookingReference(), "AUTHORIZED", "Authorization approved");
    }

    @Override
    public NotificationPreparationResponse prepareNotification(NotificationPreparationRequest request) {
      sleep();
      return new NotificationPreparationResponse("BOOKING-CONFIRMATION", "Preview for " + request.bookingReference(), "EMAIL");
    }

    @Override
    public NotificationDispatchResponse dispatchNotification(NotificationDispatchRequest request) {
      sleep();
      return new NotificationDispatchResponse("NTF-" + request.bookingReference(), "SENT");
    }

    private void sleep() {
      try {
        Thread.sleep(15);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      }
    }
  }

  private static final class RecordingBookingEventProducer extends BookingEventProducer {

    private final CopyOnWriteArrayList<BookingConfirmedEvent> events = new CopyOnWriteArrayList<>();

    private RecordingBookingEventProducer() {
      super(null, null);
    }

    @Override
    public void publish(BookingConfirmedEvent event) {
      events.add(event);
    }
  }

  private static final class RecordingBookingProcessRepository implements BookingProcessRepository {

    private final ConcurrentHashMap<String, BookingProcessInstance> instances = new ConcurrentHashMap<>();

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
          OffsetDateTime.now()
      ));
    }

    @Override
    public Optional<BookingProcessInstance> findByBookingReference(String bookingReference) {
      return Optional.ofNullable(instances.get(bookingReference));
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
          OffsetDateTime.now()
      ));
    }

    @Override
    public List<BookingProcessInstance> findByCustomerEmail(String customerEmail) {
      return instances.values().stream()
          .filter(instance -> instance.customerEmail().equalsIgnoreCase(customerEmail))
          .toList();
    }

    @Override
    public List<BookingProcessInstance> findAll() {
      return instances.values().stream().toList();
    }
  }
}
