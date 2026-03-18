package com.smartbus.booking.service;

import com.smartbus.booking.dto.ScheduleQuoteResponse;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("validateScheduleDelegate")
public class ValidateScheduleDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public ValidateScheduleDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    String bookingReference = requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE);
    ScheduleQuoteResponse quote = operations.validateSchedule(requestMapper.fromExecution(execution), bookingReference);
    double totalAmount = quote.unitPrice()
        * requestMapper.intVariable(execution, BookingWorkflowVariables.PASSENGERS)
        * ("round-trip".equalsIgnoreCase(requestMapper.stringVariable(execution, BookingWorkflowVariables.TRIP_TYPE)) ? 2 : 1);

    execution.setVariable(BookingWorkflowVariables.ROUTE_CODE, quote.routeCode());
    execution.setVariable(BookingWorkflowVariables.DEPARTURE_TIME, quote.departureTime());
    execution.setVariable(BookingWorkflowVariables.ARRIVAL_TIME, quote.arrivalTime());
    execution.setVariable(BookingWorkflowVariables.UNIT_PRICE, quote.unitPrice());
    execution.setVariable(BookingWorkflowVariables.TOTAL_AMOUNT, totalAmount);
    execution.setVariable(BookingWorkflowVariables.TRIP_AVAILABLE, quote.tripAvailable());
    execution.setVariable(BookingWorkflowVariables.RETURN_TRIP_AVAILABLE, quote.returnTripAvailable());
  }
}
