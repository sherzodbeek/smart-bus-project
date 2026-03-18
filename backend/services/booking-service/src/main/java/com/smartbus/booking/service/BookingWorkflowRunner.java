package com.smartbus.booking.service;

import com.smartbus.booking.dto.BookingRequest;

public interface BookingWorkflowRunner {

  void runBookingWorkflow(BookingRequest request, String bookingReference);
}
