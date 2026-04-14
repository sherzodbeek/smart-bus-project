package com.smartbus.schedule.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

@JacksonXmlRootElement(localName = "routeCatalog")
public record RouteCatalogResponse(
    @JacksonXmlElementWrapper(localName = "routes")
    @JacksonXmlProperty(localName = "route")
    List<RouteDefinition> routes
) {
}
