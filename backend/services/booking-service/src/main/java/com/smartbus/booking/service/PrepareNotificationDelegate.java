package com.smartbus.booking.service;

import com.smartbus.booking.dto.NotificationPreparationResponse;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("prepareNotificationDelegate")
public class PrepareNotificationDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public PrepareNotificationDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    NotificationPreparationResponse prepared = operations.prepareNotification(
        requestMapper.fromExecution(execution),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.ROUTE_CODE)
    );
    execution.setVariable(BookingWorkflowVariables.NOTIFICATION_PREVIEW, prepared.previewMessage());
    execution.setVariable(BookingWorkflowVariables.NOTIFICATION_CHANNEL, prepared.channel());
  }
}
