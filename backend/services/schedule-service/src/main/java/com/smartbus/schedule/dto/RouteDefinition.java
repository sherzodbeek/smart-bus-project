package com.smartbus.schedule.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "route")
public record RouteDefinition(
    String routeCode,
    String fromStop,
    String toStop,
    String departureTime,
    String arrivalTime,
    double unitPrice,
    int seatsAvailable
) {
}
