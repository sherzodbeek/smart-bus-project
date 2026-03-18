package com.smartbus.schedule.service;

import com.smartbus.schedule.config.ScheduleServiceProperties;
import com.smartbus.schedule.dto.ScheduleQuoteRequest;
import com.smartbus.schedule.dto.ScheduleQuoteResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CachedQuoteResponseService {

  private final ScheduleServiceProperties properties;
  private final Clock clock;
  private final Map<String, CachedValue<ScheduleQuoteResponse>> cache = new ConcurrentHashMap<>();

  @Autowired
  public CachedQuoteResponseService(ScheduleServiceProperties properties) {
    this(properties, Clock.systemUTC());
  }

  public CachedQuoteResponseService(ScheduleServiceProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public ScheduleQuoteResponse getOrCompute(ScheduleQuoteRequest request, Supplier<ScheduleQuoteResponse> supplier) {
    String key = request.fromStop() + "|" + request.toStop() + "|" + request.tripDate() + "|" + request.tripType() + "|" + request.passengers();
    CachedValue<ScheduleQuoteResponse> cachedValue = cache.get(key);
    if (cachedValue != null && cachedValue.expiresAt().isAfter(clock.instant())) {
      return cachedValue.value();
    }

    ScheduleQuoteResponse fresh = supplier.get();
    cache.put(key, new CachedValue<>(fresh, clock.instant().plus(properties.outputTtl())));
    return fresh;
  }

  public void invalidateAll() {
    cache.clear();
  }

  record CachedValue<T>(T value, Instant expiresAt) {
  }
}
