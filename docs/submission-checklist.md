# SmartBus Submission Checklist

## Source Package

- [x] `frontend/` contains the original SmartBus HTML, CSS, JS, and webpack sources
- [x] `backend/` contains the Spring Boot gateway and all backend services
- [x] `orchestration/` contains workflow definition and runtime configuration
- [x] `contracts/` contains REST and message contracts
- [x] `infra/` contains Docker Compose and database initialization assets
- [x] `docs/` contains architecture, design, evidence, and report artifacts

## Assignment Artifacts

- [x] orchestration workflow definition: `orchestration/booking-workflow.yaml`
- [x] messaging contract: `contracts/messages/booking-confirmed.v1.json`
- [x] OpenAPI contracts: `contracts/openapi/*.yaml`
- [x] correlation and state design: `docs/correlation-design.md`
- [x] caching design and evidence: `docs/cache-strategy.md`, `docs/cache-performance.md`
- [x] fault handling design and evidence: `docs/fault-strategy.md`, `docs/fault-simulations.md`
- [x] architecture report source: `docs/report/SmartBus-Integration-Architecture.md`
- [x] architecture report PDF: `docs/report/SmartBus-Integration-Architecture.pdf`

## Reproducibility

- [x] local infrastructure command documented in `README.md`
- [x] build and test command documented in `README.md`
- [x] contract validation command documented in `README.md`
- [x] backend startup order documented in `README.md`
- [x] frontend startup command documented in `README.md`
- [x] environment variables documented in `docs/runtime-plan.md`

## Reviewer Notes

- The frontend in `frontend/` is still primarily a static UI shell at this phase.
- The backend is runnable and testable independently today.
- The final end-to-end dynamic frontend-to-backend integration is planned as the closing implementation step, not yet the current runtime state.
