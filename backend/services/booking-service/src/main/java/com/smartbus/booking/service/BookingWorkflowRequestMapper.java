package com.smartbus.booking.service;

import com.smartbus.booking.dto.BookingRequest;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class BookingWorkflowRequestMapper {

  public BookingRequest fromExecution(DelegateExecution execution) {
    return new BookingRequest(
        stringVariable(execution, BookingWorkflowVariables.CUSTOMER_NAME),
        stringVariable(execution, BookingWorkflowVariables.CUSTOMER_EMAIL),
        stringVariable(execution, BookingWorkflowVariables.FROM_STOP),
        stringVariable(execution, BookingWorkflowVariables.TO_STOP),
        stringVariable(execution, BookingWorkflowVariables.TRIP_DATE),
        stringVariable(execution, BookingWorkflowVariables.TRIP_TYPE),
        intVariable(execution, BookingWorkflowVariables.PASSENGERS),
        stringVariable(execution, BookingWorkflowVariables.PAYMENT_METHOD_TOKEN)
    );
  }

  public String stringVariable(DelegateExecution execution, String name) {
    return String.valueOf(execution.getVariable(name));
  }

  public int intVariable(DelegateExecution execution, String name) {
    Object value = execution.getVariable(name);
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  public double doubleVariable(DelegateExecution execution, String name) {
    Object value = execution.getVariable(name);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }

  public boolean booleanVariable(DelegateExecution execution, String name) {
    Object value = execution.getVariable(name);
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
