package com.smartbus.booking.service;

import com.smartbus.booking.config.BookingOrchestrationProperties;
import com.smartbus.booking.dto.NotificationDispatchRequest;
import com.smartbus.booking.dto.NotificationDispatchResponse;
import com.smartbus.booking.dto.NotificationPreparationRequest;
import com.smartbus.booking.dto.NotificationPreparationResponse;
import com.smartbus.booking.dto.PaymentAuthorizationRequest;
import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import com.smartbus.booking.dto.ScheduleQuoteRequest;
import com.smartbus.booking.dto.ScheduleQuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PartnerServiceClient implements BookingPartnerGateway {

  private static final Logger log = LoggerFactory.getLogger(PartnerServiceClient.class);

  private final RestClient restClient;
  private final BookingOrchestrationProperties properties;

  public PartnerServiceClient(RestClient.Builder builder, BookingOrchestrationProperties properties) {
    this.restClient = builder.build();
    this.properties = properties;
  }

  @Override
  public ScheduleQuoteResponse quoteTrip(ScheduleQuoteRequest request) {
    log.info(
        "partnerHttpRequest service=schedule-service operation=quote-trip fromStop={} toStop={} tripDate={} tripType={} passengers={}",
        request.fromStop(),
        request.toStop(),
        request.tripDate(),
        request.tripType(),
        request.passengers()
    );
    return restClient.post()
        .uri(properties.schedule().resolve("/api/v1/schedules/quote"))
        .body(request)
        .retrieve()
        .body(ScheduleQuoteResponse.class);
  }

  @Override
  public PaymentAuthorizationResponse authorizePayment(PaymentAuthorizationRequest request) {
    log.info(
        "partnerHttpRequest service=payment-service operation=authorize-payment bookingReference={} customerEmail={} amount={}",
        request.bookingReference(),
        request.customerEmail(),
        request.amount()
    );
    return restClient.post()
        .uri(properties.payment().resolve("/api/v1/payments/authorize"))
        .body(request)
        .retrieve()
        .body(PaymentAuthorizationResponse.class);
  }

  @Override
  public NotificationPreparationResponse prepareNotification(NotificationPreparationRequest request) {
    log.info(
        "partnerHttpRequest service=notification-service operation=prepare-notification bookingReference={} customerEmail={} routeCode={}",
        request.bookingReference(),
        request.customerEmail(),
        request.routeCode()
    );
    return restClient.post()
        .uri(properties.notification().resolve("/api/v1/notifications/prepare"))
        .body(request)
        .retrieve()
        .body(NotificationPreparationResponse.class);
  }

  @Override
  public NotificationDispatchResponse dispatchNotification(NotificationDispatchRequest request) {
    log.info(
        "partnerHttpRequest service=notification-service operation=dispatch-notification bookingReference={} recipient={}",
        request.bookingReference(),
        request.recipient()
    );
    return restClient.post()
        .uri(properties.notification().resolve("/api/v1/notifications/dispatch"))
        .body(request)
        .retrieve()
        .body(NotificationDispatchResponse.class);
  }
}
