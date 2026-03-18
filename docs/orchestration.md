# SmartBus Booking Orchestration

## Overview

SmartBus now runs the booking workflow on a real BPMN process engine with Flowable inside `booking-service`.

Core artifacts:

- Executable BPMN model: `backend/services/booking-service/src/main/resources/processes/smartbus-booking.bpmn20.xml`
- Runtime entrypoint: `POST /api/v1/bookings/orchestrated-bookings`
- BPMN runner: `FlowableBookingWorkflowRunner`
- Delegate implementations: `InitializeBookingDelegate`, `ValidateScheduleDelegate`, `AuthorizePaymentDelegate`, `PrepareNotificationDelegate`, `CheckPaymentDecisionDelegate`, `DispatchNotificationDelegate`, `FinalizeBookingDelegate`

The booking service remains the process owner and coordinates:

- `schedule-service`
- `payment-service`
- `notification-service`

## What Runs in Flowable

The BPMN process key is `smartbusBookingProcess`.

The deployed process contains:

- `startEvent`: receive booking request
- `serviceTask`: initialize booking state
- `serviceTask`: validate schedule
- `serviceTask`: record start of the parallel branch
- `parallelGateway`: split payment authorization and notification preparation
- `parallelGateway`: join both branches
- `serviceTask`: evaluate payment result
- `serviceTask`: dispatch notification
- `serviceTask`: finalize booking and publish Kafka event
- `endEvent`: reply to caller

Each BPMN service task is wired through `flowable:delegateExpression`, so the engine executes Spring-managed Java delegates at runtime.

## Workflow Semantics

The BPMN implementation covers the orchestration concepts required by the assignment:

- `receive`: `startEvent` plus controller handoff
- `invoke`: delegate tasks call partner services through `BookingPartnerGateway`
- `sequence`: the main booking path remains ordered
- `flow`: payment authorization and notification preparation run through a BPMN parallel gateway
- `switch`: business branching is evaluated in workflow operations during schedule and payment checks
- `reply`: the process completes and the booking controller returns the assembled response

## Process State

The business correlation key is `bookingReference`.

The booking-service database persists lifecycle state after major transitions:

1. `RECEIVED`
2. `SCHEDULE_VALIDATED`
3. `ROUND_TRIP_VALIDATED`
4. `PAYMENT_PENDING`
5. `PAYMENT_AUTHORIZED`
6. `NOTIFICATION_PENDING`
7. `CONFIRMED`
8. `FAILED`

This state is separate from Flowable runtime tables. Flowable manages BPMN execution, while SmartBus persists business-facing booking state for APIs, correlation, and recovery.

## Supporting Artifacts

`orchestration/booking-workflow.yaml` is now a supplementary summary of the process for reviewers. It is not the executable workflow. The executable definition is the BPMN file loaded by Flowable at startup.

## Failure Handling

The process fails when:

- the outbound trip is unavailable
- a round-trip request has no return availability
- payment authorization is declined
- a downstream service times out or exhausts retries

When that happens, booking-service records `FAILED`, logs the `bookingReference`, and returns the mapped HTTP error response.

## Logging Evidence

Workflow execution still emits structured step logs in this format:

`workflowStep bookingReference={id} step={step} element={element} detail={detail}`

That gives runtime evidence that the BPMN-driven process reached the expected orchestration elements.
