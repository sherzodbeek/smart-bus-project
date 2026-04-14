package com.smartbus.schedule.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "scheduleQuote")
public record ScheduleQuoteResponse(
    boolean tripAvailable,
    boolean returnTripAvailable,
    String routeCode,
    String departureTime,
    String arrivalTime,
    double unitPrice,
    int availableSeats
) {
}
