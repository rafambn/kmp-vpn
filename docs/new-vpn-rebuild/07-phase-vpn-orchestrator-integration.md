# Phase 07: VPN Orchestrator Integration

## Objective

Finalize `Vpn` orchestration semantics across `SessionManager` and `VpnInterface`, then complete daemon-backed execution cutover.

## Implementation Status

Status: Re-scoped (partially implemented)  
As Of: 2026-04-02

## Implemented in Current Code

1. `Vpn` constructor supports production defaults plus injectable test dependencies.
2. Core lifecycle orchestration is implemented:
- `create`: ensure interface exists and reconcile sessions
- `start`: validate/configure, reconcile sessions, and bring interface up
- `stop`: close sessions then bring interface down
- `delete`: idempotent stop/close/delete flow with best-effort unwind ordering
3. Health and state APIs are implemented:
- `exists`
- `isRunning`
- `state` (observational: `NotCreated`, `Created`, `Running`)
- `configuration`
4. `reconfigure` updates interface configuration and re-runs session reconciliation.
5. Error mapping publishes `VpnEvent.Failure`; non-fatal lifecycle conflicts publish `VpnEvent.Alert`.
6. Tests cover lifecycle transitions, idempotency, and invariant behavior.

## Remaining Scope

1. Wire daemon-backed `InterfaceCommandExecutor` into JVM production factory/cutover path.
2. Add integration tests for orchestrator lifecycle against a real daemon-backed executor.
3. Confirm and document long-term packet-loop ownership boundary versus orchestrator lifecycle.

## Deliverables

1. Core orchestrator-based `Vpn` implementation in `:new-vpn` (completed).
2. Merged interface-configuration policy validated and documented (completed).
3. Lifecycle integration tests with daemon-backed path (pending).
4. Finalized packet-loop ownership/cutover docs after daemon wiring (pending).

## Exit Criteria

1. Public lifecycle API works against daemon-backed JVM path.
2. Rollback tests pass for failure boundaries including daemon-backed command failures.
3. `Vpn` maintains strict delegation to `SessionManager` and `VpnInterface` contracts.

## Risks and Controls

1. Risk: lifecycle race conditions.  
Control: keep explicit idempotent transitions and deterministic error mapping.
2. Risk: hidden coupling returns through convenience methods.  
Control: enforce contract boundaries and integration tests during daemon cutover.
