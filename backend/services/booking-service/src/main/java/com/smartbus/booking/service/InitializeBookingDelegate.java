package com.smartbus.booking.service;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("initializeBookingDelegate")
public class InitializeBookingDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public InitializeBookingDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    operations.initializeBooking(
        requestMapper.fromExecution(execution),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE)
    );
  }
}
