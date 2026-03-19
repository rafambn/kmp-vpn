# Phase 02: Domain Model and Contracts

## Objective

Define the final core contracts before platform implementation starts.

## Scope

1. Design `Vpn` orchestration surface.
2. Design `SessionManager` contracts.
3. Design `VpnInterface` contracts and state model.
4. Define error and event model shared by core and JVM.

## Contract Set

1. `VpnState` (sealed class): `NotCreated`, `Created`, `Running`, `Stopping`, `Deleted`, `Failed`.
2. `SessionManager`:
- `ensureSessions(config)`
- `reconcileSessions(config)`
- `sessions()`
- `session(peerKey)`
- `closeAll()`
3. `VpnInterface`:
- `exists(interfaceName)`
- `create(config)`
- `up()` / `down()`
- `delete()`
- `isUp()`
- `applyConfiguration(config)`
- `readInformation()`
4. `VpnLifecycleError` (sealed class) for orchestrator-level failures.
5. `VpnEvent` stream model for lifecycle telemetry.

## Work Breakdown

1. Move mutable lifecycle state out of `VpnAdapter` into explicit models.
2. Create new interfaces in `commonMain` only.
3. Add KDoc for all public contracts.
4. Define invariants:
- interface name non-empty
- unique peer public keys
- idempotent `stop`/`delete`

## Deliverables

1. Compiling contract-first API.
2. Unit tests for invariants and model transitions.
3. Architecture decision note freezing contract semantics for phases 03-04.

## Exit Criteria

1. Contracts are stable and reviewed.
2. No platform command or daemon concerns leak into core contracts.
3. Test coverage exists for all transition and invariant rules.

## Risks and Controls

1. Risk: overfitting to current placeholder behavior.
Control: design for eventual real interface/session backends.
2. Risk: contract ambiguity.
Control: write tests that encode expected lifecycle semantics.
