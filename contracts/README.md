# Service Contracts

This directory contains the formal SmartBus integration contracts.

Contents:

- `openapi/*.v1.yaml`: versioned REST contracts for gateway and backend services
- `messages/*.json`: versioned JSON Schema event contracts
- `validate-openapi.sh`: validation command wrapper used from the repository root

There are no SOAP interfaces in the current SmartBus backend, so `contracts/wsdl/` is intentionally absent.
