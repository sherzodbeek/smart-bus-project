package com.smartbus.booking.service;

import com.smartbus.booking.dto.NotificationDispatchResponse;
import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import com.smartbus.booking.dto.ScheduleQuoteResponse;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("finalizeBookingDelegate")
public class FinalizeBookingDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public FinalizeBookingDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    operations.finalizeBooking(
        requestMapper.fromExecution(execution),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE),
        new ScheduleQuoteResponse(
            requestMapper.booleanVariable(execution, BookingWorkflowVariables.TRIP_AVAILABLE),
            requestMapper.booleanVariable(execution, BookingWorkflowVariables.RETURN_TRIP_AVAILABLE),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.ROUTE_CODE),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.DEPARTURE_TIME),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.ARRIVAL_TIME),
            requestMapper.doubleVariable(execution, BookingWorkflowVariables.UNIT_PRICE),
            0
        ),
        requestMapper.doubleVariable(execution, BookingWorkflowVariables.TOTAL_AMOUNT),
        new PaymentAuthorizationResponse(
            requestMapper.booleanVariable(execution, BookingWorkflowVariables.PAYMENT_APPROVED),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_TRANSACTION_ID),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_STATUS),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_REASON)
        ),
        new NotificationDispatchResponse(
            requestMapper.stringVariable(execution, BookingWorkflowVariables.NOTIFICATION_ID),
            "SENT"
        )
    );
  }
}
