# Phase 02: Domain Model and Contracts

## Objective

Define the final core contracts before platform implementation starts.

## Scope

1. Design `Vpn` orchestration surface.
2. Design `SessionManager` contracts.
3. Design `VpnInterface` contracts and state model.
4. Define error and event model shared by core and JVM.

## Contract Set

1. `VpnState` (sealed class): observational states `NotCreated`, `Created`, `Running`.
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
- `configuration()`
- `reconfigure(config)`
- `readInformation()`
4. `VpnEvent` stream model for lifecycle telemetry (`Alert(message)`, `Failure(message)`).
   Failure and delete semantics are emitted as events/errors instead of persisted synthetic states.

## Work Breakdown

1. Merge `VpnAdapter` responsibilities into `VpnInterface` and keep lifecycle state in explicit models.
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
