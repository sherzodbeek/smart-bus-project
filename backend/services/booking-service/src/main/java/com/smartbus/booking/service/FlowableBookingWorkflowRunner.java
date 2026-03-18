package com.smartbus.booking.service;

import com.smartbus.booking.dto.BookingRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class FlowableBookingWorkflowRunner implements BookingWorkflowRunner {

  private static final Logger log = LoggerFactory.getLogger(FlowableBookingWorkflowRunner.class);

  private final RuntimeService runtimeService;
  private final BookingWorkflowOperations operations;

  public FlowableBookingWorkflowRunner(RuntimeService runtimeService, BookingWorkflowOperations operations) {
    this.runtimeService = runtimeService;
    this.operations = operations;
  }

  @Override
  public void runBookingWorkflow(BookingRequest request, String bookingReference) {
    try {
      log.info("flowableWorkflowStart bookingReference={} processKey=smartbusBookingProcess", bookingReference);
      runtimeService.startProcessInstanceByKey("smartbusBookingProcess", variables(request, bookingReference));
      log.info("flowableWorkflowSubmitted bookingReference={}", bookingReference);
    } catch (RuntimeException exception) {
      Throwable failure = unwrap(exception);
      log.error(
          "flowableWorkflowFailed bookingReference={} exceptionType={} message={}",
          bookingReference,
          failure.getClass().getSimpleName(),
          failure.getMessage()
      );
      operations.handleFailure(bookingReference, failure);
      if (failure instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(failure);
    }
  }

  private Map<String, Object> variables(BookingRequest request, String bookingReference) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(BookingWorkflowVariables.BOOKING_REFERENCE, bookingReference);
    variables.put(BookingWorkflowVariables.CUSTOMER_NAME, request.customerName());
    variables.put(BookingWorkflowVariables.CUSTOMER_EMAIL, request.customerEmail());
    variables.put(BookingWorkflowVariables.FROM_STOP, request.fromStop());
    variables.put(BookingWorkflowVariables.TO_STOP, request.toStop());
    variables.put(BookingWorkflowVariables.TRIP_DATE, request.tripDate());
    variables.put(BookingWorkflowVariables.TRIP_TYPE, request.tripType());
    variables.put(BookingWorkflowVariables.PASSENGERS, request.passengers());
    variables.put(BookingWorkflowVariables.PAYMENT_METHOD_TOKEN, request.paymentMethodToken());
    return variables;
  }

  private Throwable unwrap(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
