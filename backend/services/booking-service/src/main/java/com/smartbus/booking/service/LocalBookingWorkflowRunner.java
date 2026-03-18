package com.smartbus.booking.service;

import com.smartbus.booking.dto.BookingRequest;
import com.smartbus.booking.dto.NotificationDispatchResponse;
import com.smartbus.booking.dto.NotificationPreparationResponse;
import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import com.smartbus.booking.dto.ScheduleQuoteResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LocalBookingWorkflowRunner implements BookingWorkflowRunner {

  private final BookingWorkflowOperations operations;
  private final Executor orchestrationExecutor;

  public LocalBookingWorkflowRunner(BookingWorkflowOperations operations, Executor orchestrationExecutor) {
    this.operations = operations;
    this.orchestrationExecutor = orchestrationExecutor;
  }

  @Override
  public void runBookingWorkflow(BookingRequest request, String bookingReference) {
    operations.initializeBooking(request, bookingReference);
    try {
      ScheduleQuoteResponse quote = operations.validateSchedule(request, bookingReference);
      double totalAmount = quote.unitPrice() * request.passengers() * ("round-trip".equalsIgnoreCase(request.tripType()) ? 2 : 1);

      operations.recordParallelFlowStart(bookingReference);
      CompletableFuture<PaymentAuthorizationResponse> paymentFuture = CompletableFuture.supplyAsync(
          () -> operations.authorizePayment(request, bookingReference, totalAmount),
          orchestrationExecutor
      );
      CompletableFuture<NotificationPreparationResponse> preparationFuture = CompletableFuture.supplyAsync(
          () -> operations.prepareNotification(request, bookingReference, quote.routeCode()),
          orchestrationExecutor
      );

      PaymentAuthorizationResponse payment = paymentFuture.join();
      NotificationPreparationResponse prepared = preparationFuture.join();

      operations.checkPaymentDecision(bookingReference, payment);
      NotificationDispatchResponse notification = operations.dispatchNotification(
          bookingReference,
          request.customerEmail(),
          prepared.previewMessage(),
          payment.transactionId()
      );
      operations.finalizeBooking(request, bookingReference, quote, totalAmount, payment, notification);
    } catch (RuntimeException exception) {
      Throwable failure = unwrap(exception);
      operations.handleFailure(bookingReference, failure);
      if (failure instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(failure);
    }
  }

  private Throwable unwrap(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null
        && (current instanceof java.util.concurrent.CompletionException
        || current instanceof java.util.concurrent.ExecutionException)) {
      current = current.getCause();
    }
    return current;
  }
}
