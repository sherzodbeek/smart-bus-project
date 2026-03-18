package com.smartbus.booking.service;

import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("checkPaymentDecisionDelegate")
public class CheckPaymentDecisionDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public CheckPaymentDecisionDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    operations.checkPaymentDecision(
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE),
        new PaymentAuthorizationResponse(
            requestMapper.booleanVariable(execution, BookingWorkflowVariables.PAYMENT_APPROVED),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_TRANSACTION_ID),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_STATUS),
            requestMapper.stringVariable(execution, BookingWorkflowVariables.PAYMENT_REASON)
        )
    );
  }
}
