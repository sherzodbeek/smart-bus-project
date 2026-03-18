package com.smartbus.booking.service;

import com.smartbus.booking.dto.WorkflowLogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class BookingWorkflowTraceStore {

  private final Map<String, List<WorkflowLogEntry>> tracesByBookingReference = new ConcurrentHashMap<>();

  public void append(String bookingReference, WorkflowLogEntry entry) {
    tracesByBookingReference.computeIfAbsent(bookingReference, ignored -> new ArrayList<>());
    synchronized (tracesByBookingReference.get(bookingReference)) {
      tracesByBookingReference.get(bookingReference).add(entry);
    }
  }

  public List<WorkflowLogEntry> snapshot(String bookingReference) {
    List<WorkflowLogEntry> entries = tracesByBookingReference.get(bookingReference);
    if (entries == null) {
      return List.of();
    }
    synchronized (entries) {
      return List.copyOf(entries);
    }
  }
}
