package com.smartbus.booking.service;

import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("authorizePaymentDelegate")
public class AuthorizePaymentDelegate implements JavaDelegate {

  private final BookingWorkflowOperations operations;
  private final BookingWorkflowRequestMapper requestMapper;

  public AuthorizePaymentDelegate(BookingWorkflowOperations operations, BookingWorkflowRequestMapper requestMapper) {
    this.operations = operations;
    this.requestMapper = requestMapper;
  }

  @Override
  public void execute(DelegateExecution execution) {
    PaymentAuthorizationResponse payment = operations.authorizePayment(
        requestMapper.fromExecution(execution),
        requestMapper.stringVariable(execution, BookingWorkflowVariables.BOOKING_REFERENCE),
        requestMapper.doubleVariable(execution, BookingWorkflowVariables.TOTAL_AMOUNT)
    );
    execution.setVariable(BookingWorkflowVariables.PAYMENT_APPROVED, payment.approved());
    execution.setVariable(BookingWorkflowVariables.PAYMENT_STATUS, payment.status());
    execution.setVariable(BookingWorkflowVariables.PAYMENT_REASON, payment.reason());
    execution.setVariable(BookingWorkflowVariables.PAYMENT_TRANSACTION_ID, payment.transactionId());
  }
}
