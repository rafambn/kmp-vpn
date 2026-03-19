# Phase 03: SessionManager and Transport Strategy

## Objective

Implement transport/session lifecycle as an isolated domain service.

## Scope

1. Build `SessionManager` with full peer-session reconciliation.
2. Integrate UniFFI `TunnelSession` through a clean Kotlin wrapper.
3. Add strategy selection based on engine and peer count.

## Work Breakdown

1. Create `VpnSession` abstraction (per peer session handle).
2. Add `ManagedSession` registry keyed by peer public key.
3. Implement core operations:
- `ensureSessions`
- `reconcileSessions`
- `closeSession`
- `closeAll`
4. Implement deterministic session index generation.
5. Add `TransportStrategy` hierarchy:
- `BoringTunSinglePeerStrategy`
- `BoringTunMultiPeerStrategy`
- `QuicPlaceholderStrategy`
6. Connect strategy to existing Rust tunnel binding where available.

## Deliverables

1. `SessionManager` implementation in `commonMain`.
2. JVM actual session factory implementation backed by UniFFI.
3. Unit tests for:
- duplicate peer key rejection
- stale session removal
- config change reconciliation
- partial-create rollback

## Exit Criteria

1. `Vpn` can delegate session lifecycle to `SessionManager` without platform dependencies.
2. Session behavior is deterministic and idempotent.
3. Failure paths close newly-created sessions on rollback.

## Risks and Controls

1. Risk: memory/resource leaks in session replacement.
Control: mandatory close on stale and failed sessions.
2. Risk: mismatched transport semantics for QUIC.
Control: explicit unsupported strategy with clear error path.
