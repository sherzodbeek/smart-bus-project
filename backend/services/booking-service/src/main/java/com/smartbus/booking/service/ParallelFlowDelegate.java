package com.smartbus.booking.service;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("parallelFlowDelegate")
public class ParallelFlowDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public ParallelFlowDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    operations.recordParallelFlowStart(
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE)
    );
  }
}
