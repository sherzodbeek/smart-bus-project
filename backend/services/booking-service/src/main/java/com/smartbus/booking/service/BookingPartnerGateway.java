package com.smartbus.booking.service;

import com.smartbus.booking.dto.NotificationDispatchRequest;
import com.smartbus.booking.dto.NotificationDispatchResponse;
import com.smartbus.booking.dto.NotificationPreparationRequest;
import com.smartbus.booking.dto.NotificationPreparationResponse;
import com.smartbus.booking.dto.PaymentAuthorizationRequest;
import com.smartbus.booking.dto.PaymentAuthorizationResponse;
import com.smartbus.booking.dto.ScheduleQuoteRequest;
import com.smartbus.booking.dto.ScheduleQuoteResponse;

public interface BookingPartnerGateway {

  ScheduleQuoteResponse quoteTrip(ScheduleQuoteRequest request);

  PaymentAuthorizationResponse authorizePayment(PaymentAuthorizationRequest request);

  NotificationPreparationResponse prepareNotification(NotificationPreparationRequest request);

  NotificationDispatchResponse dispatchNotification(NotificationDispatchRequest request);
}
