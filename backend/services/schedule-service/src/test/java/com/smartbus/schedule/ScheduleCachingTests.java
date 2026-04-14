package com.smartbus.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbus.schedule.config.ScheduleServiceProperties;
import com.smartbus.schedule.controller.ScheduleOrchestrationController;
import com.smartbus.schedule.dto.FareUpdateRequest;
import com.smartbus.schedule.dto.LocationResponse;
import com.smartbus.schedule.dto.RouteCatalogResponse;
import com.smartbus.schedule.dto.ScheduleQuoteRequest;
import com.smartbus.schedule.dto.ScheduleQuoteResponse;
import com.smartbus.schedule.service.CachedQuoteResponseService;
import com.smartbus.schedule.service.ScheduleCatalogService;
import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest(
    properties = {
        "smartbus.cache.data-ttl=PT5M",
        "smartbus.cache.output-ttl=PT30S",
        "smartbus.cache.simulated-latency=PT0.06S"
    }
)
class ScheduleCachingTests {

  @Autowired
  private ScheduleCatalogService scheduleCatalogService;

  @Autowired
  private CacheManager cacheManager;

  @Autowired
  private ScheduleOrchestrationController scheduleOrchestrationController;

  @BeforeEach
  void clearCaches() {
    cacheManager.getCache("routeCatalog").clear();
    cacheManager.getCache("routeDefinition").clear();
    cacheManager.getCache("locationCatalog").clear();
    cacheManager.getCache("locationById").clear();
  }

  @Test
  void dataCacheImprovesCatalogReads() {
    long coldStart = System.nanoTime();
    RouteCatalogResponse cold = scheduleCatalogService.catalog();
    long coldDuration = System.nanoTime() - coldStart;

    long warmStart = System.nanoTime();
    RouteCatalogResponse warm = scheduleCatalogService.catalog();
    long warmDuration = System.nanoTime() - warmStart;

    System.out.println("catalogColdMs=" + Duration.ofNanos(coldDuration).toMillis());
    System.out.println("catalogWarmMs=" + Duration.ofNanos(warmDuration).toMillis());

    assertEquals(cold, warm);
    assertTrue(coldDuration > warmDuration);
  }

  @Test
  void outputCacheImprovesRepeatedQuoteResponses() {
    ScheduleServiceProperties properties = new ScheduleServiceProperties(
        Duration.ofMinutes(5),
        Duration.ofSeconds(30),
        Duration.ofMillis(60)
    );
    Clock clock = Clock.fixed(Instant.parse("2026-03-15T00:00:00Z"), ZoneOffset.UTC);
    CachedQuoteResponseService responseCache = new CachedQuoteResponseService(properties, clock);
    ScheduleQuoteRequest request = new ScheduleQuoteRequest(
        "Downtown Terminal",
        "Airport Station",
        "2026-03-28",
        "one-way",
        1
    );
    AtomicInteger supplierInvocations = new AtomicInteger();
    ScheduleQuoteResponse expected = new ScheduleQuoteResponse(
        true,
        true,
        "SB-101",
        "08:00 AM",
        "09:15 AM",
        12.50,
        23
    );

    long coldStart = System.nanoTime();
    ScheduleQuoteResponse cold = responseCache.getOrCompute(request, () -> {
      supplierInvocations.incrementAndGet();
      pause(Duration.ofMillis(60));
      return expected;
    });
    long coldDuration = System.nanoTime() - coldStart;

    long warmStart = System.nanoTime();
    ScheduleQuoteResponse warm = responseCache.getOrCompute(request, () -> {
      throw new IllegalStateException("Should not recompute cached quote");
    });
    long warmDuration = System.nanoTime() - warmStart;

    System.out.println("quoteColdMs=" + Duration.ofNanos(coldDuration).toMillis());
    System.out.println("quoteWarmMs=" + Duration.ofNanos(warmDuration).toMillis());

    assertEquals(cold, warm);
    assertEquals(1, supplierInvocations.get());
    assertTrue(coldDuration > warmDuration);
  }

  @Test
  void fareUpdateInvalidatesCatalogAndQuoteCaches() {
    ScheduleQuoteRequest request = new ScheduleQuoteRequest(
        "Downtown Terminal",
        "Airport Station",
        "2026-03-28",
        "one-way",
        1
    );

    ScheduleQuoteResponse cachedQuote = scheduleOrchestrationController.quote(request);
    RouteCatalogResponse cachedCatalog = scheduleCatalogService.catalog();

    scheduleOrchestrationController.updateFare("SB-101", new FareUpdateRequest(18.75));

    long catalogRefreshStart = System.nanoTime();
    RouteCatalogResponse refreshedCatalog = scheduleCatalogService.catalog();
    long catalogRefreshDuration = System.nanoTime() - catalogRefreshStart;

    long quoteRefreshStart = System.nanoTime();
    ScheduleQuoteResponse refreshedQuote = scheduleOrchestrationController.quote(request);
    long quoteRefreshDuration = System.nanoTime() - quoteRefreshStart;

    assertTrue(cachedCatalog.routes().stream().anyMatch(route -> route.routeCode().equals("SB-101") && route.unitPrice() == 12.50));
    assertTrue(refreshedCatalog.routes().stream().anyMatch(route -> route.routeCode().equals("SB-101") && route.unitPrice() == 18.75));
    assertEquals(12.50, cachedQuote.unitPrice());
    assertEquals(18.75, refreshedQuote.unitPrice());
    assertTrue(Duration.ofNanos(catalogRefreshDuration).toMillis() >= 60);
    assertTrue(Duration.ofNanos(quoteRefreshDuration).toMillis() >= 60);
  }

  @Test
  void locationCatalogCacheImprovesCatalogReads() {
    long coldStart = System.nanoTime();
    List<LocationResponse> cold = scheduleCatalogService.locations();
    long coldDuration = System.nanoTime() - coldStart;

    long warmStart = System.nanoTime();
    List<LocationResponse> warm = scheduleCatalogService.locations();
    long warmDuration = System.nanoTime() - warmStart;

    System.out.println("locationCatalogColdMs=" + Duration.ofNanos(coldDuration).toMillis());
    System.out.println("locationCatalogWarmMs=" + Duration.ofNanos(warmDuration).toMillis());

    assertEquals(cold, warm);
    assertTrue(coldDuration > warmDuration);
  }

  @Test
  void locationCacheInvalidatedOnLocationCreate() {
    List<LocationResponse> before = scheduleCatalogService.locations();

    scheduleOrchestrationController.createLocation(
        new com.smartbus.schedule.dto.LocationRequest("New Test City"));

    long refreshStart = System.nanoTime();
    List<LocationResponse> after = scheduleCatalogService.locations();
    long refreshDuration = System.nanoTime() - refreshStart;

    System.out.println("locationCreateInvalidationRefreshMs=" + Duration.ofNanos(refreshDuration).toMillis());

    assertTrue(after.size() > before.size());
    assertTrue(after.stream().anyMatch(l -> l.name().equals("New Test City")));
    assertTrue(Duration.ofNanos(refreshDuration).toMillis() >= 60);
  }

  private void pause(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
