package com.smartbus.booking.dto;

public record WorkflowLogEntry(
    String step,
    String element,
    String detail
) {
}
