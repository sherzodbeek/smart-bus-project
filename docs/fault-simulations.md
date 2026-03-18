# SmartBus Fault Simulations

## Test Evidence

Fault simulations were executed through `BookingOrchestrationServiceTests`.

Command:

```sh
mvn -Dmaven.repo.local=.m2/repository -pl backend/services/booking-service test
```

## Scenario 1: Transient Notification Preparation Failure

Setup:

- `notification-service` preparation failed twice with `ResourceAccessException`
- retry policy remained at `3` attempts

Observed result:

- booking completed successfully
- notification preparation succeeded on the third attempt
- booking state ended in `CONFIRMED`

Observed log pattern:

- `partnerCallFailure ... service=notification-service operation=prepare-notification attempt=1 code=DOWNSTREAM_UNAVAILABLE`
- `partnerCallRetry ... attempt=1 backoffMs=10`
- `partnerCallRetry ... attempt=2 backoffMs=20`
- `partnerCallSuccess ... service=notification-service operation=prepare-notification attempt=3`

## Scenario 2: Schedule Service Timeout

Setup:

- schedule quote call delayed to `60 ms`
- timeout lowered to `20 ms`
- retry policy set to `2` attempts

Observed result:

- booking failed gracefully with `PartnerServiceException`
- final error code: `DOWNSTREAM_TIMEOUT`
- final HTTP mapping: `504`
- persisted booking state ended in `FAILED`

Observed log pattern:

- `partnerCallStart ... service=schedule-service operation=quote-trip attempt=1 timeoutMs=20`
- `partnerCallFailure ... code=DOWNSTREAM_TIMEOUT`
- `partnerCallRetry ... attempt=1 backoffMs=5`
- `partnerCallFailure ... attempt=2 code=DOWNSTREAM_TIMEOUT`
- `bookingWorkflowFailed ... exceptionType=PartnerServiceException`

## Scenario 3: Parallel Isolation Still Holds Under Fault Middleware

Setup:

- existing concurrency test executed with the new fault-handling layer active

Observed result:

- booking references remained unique
- every successful booking preserved its own correlation key
- state persistence and Kafka event publishing counts still matched completed bookings

## Conclusion

The implemented timeout and retry strategy is observable in logs, bounded in behavior, and does not crash the booking process. Failures terminate in a persisted `FAILED` state and can be communicated back to clients as structured error responses.
