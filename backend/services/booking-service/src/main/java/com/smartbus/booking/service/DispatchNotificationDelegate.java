package com.smartbus.booking.service;

import com.smartbus.booking.dto.NotificationDispatchResponse;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

//@Component("dispatchNotificationDelegate")
public class DispatchNotificationDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public DispatchNotificationDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    NotificationDispatchResponse notification = operations.dispatchNotification(
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.CUSTOMER_EMAIL),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.NOTIFICATION_PREVIEW),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_TRANSACTION_ID)
    );
    execution.setVariable(BookingWorkflowVariables.NOTIFICATION_ID, notification.notificationId());
  }
}
