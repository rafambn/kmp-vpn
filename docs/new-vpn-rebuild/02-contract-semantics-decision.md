# ADR-02: Core Contract Semantics Freeze (Phases 03-04)

Date: 2026-03-19
Status: Accepted

## Context

Phase 02 defines the stable core contracts that phases 03 and 04 must implement.
Without a semantic freeze, session and interface layers can drift and force repeated
orchestrator redesign.

## Decision

1. `VpnState` is observational with states: `NotCreated`, `Created`, `Running`.
2. `Vpn` owns state transitions and emits `VpnEvent` telemetry events through callback stream (`onEvent`) without internal buffering.
3. `SessionManager` owns peer session synchronization through `ensureSessions`, `reconcileSessions`, query APIs, and `closeAll`.
4. `VpnInterface` owns interface lifecycle and runtime configuration (merged adapter semantics).
5. Invariants are mandatory for orchestration:
- interface name must be non-blank
- peer public keys must be unique
- `stop` and `delete` are idempotent

## Consequences

1. Phase 03 can implement transport/session behavior behind `SessionManager` without changing `Vpn` surface.
2. Phase 04 can implement platform interface behavior behind `VpnInterface` without changing orchestrator contracts.
3. New failures surface through `VpnEvent.Failure(message)` with human-readable descriptions; no persisted synthetic `Failed` state is kept.
