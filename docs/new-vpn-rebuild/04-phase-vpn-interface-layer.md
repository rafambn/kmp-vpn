# Phase 04: VpnInterface and Platform Edge Adapters

## Objective

Implement OS interaction behind a dedicated `VpnInterface` boundary and provide platform adapters for packet I/O ports.

## Implementation Status

Status: Completed (re-scoped)  
Date: 2026-03-20 (reconciled on 2026-04-02)

## Scope

1. Build interface lifecycle abstraction independent of session code.
2. Provide JVM implementation that can run with real or stub executors.
3. Implement merged interface+configuration semantics in `VpnInterface`.
4. Deliver concrete packet-loop edge adapters where practical, keeping remaining platform bindings behind common contracts.

## Work Breakdown

1. Define `PlatformInterfaceFactory` (`expect/actual`) for per-target `VpnInterface` creation.
2. Implement `JvmVpnInterface` with operations:
- create/check/delete interface
- up/down state
- apply MTU/address/routes/DNS
- reconfigure peer configuration
- read interface information and peer stats
3. Provide packet loop edge adapters and test doubles:
- `KtorDatagramUdpPort` implementation for `UdpPort` in `commonMain`
- `TunPort` and periodic ticker test doubles for deterministic loop testing
4. Add `InterfaceCommandExecutor` boundary so daemon migration can plug in later.
5. Implement fake in-memory interface and fake packet ports for fast `commonTest`.
6. Defer OS-specific command packs and concrete `TunPort` integrations to later phases.

## Deliverables

1. Core `VpnInterface` contract and factory.
2. JVM interface implementation wired to executor abstraction.
3. Packet I/O adapter baseline wired to common loop contracts (`KtorDatagramUdpPort` + test fakes).
4. Tests validating idempotency and rollback behavior.

## Exit Criteria

1. `Vpn` lifecycle runs through a single `VpnInterface` contract (no separate adapter type).
2. Session lifecycle and interface lifecycle are independent modules/classes.
3. Packet loop can run with platform adapters without daemon ownership.
4. Existing minimal API remains callable via orchestrator.

## Risks and Controls

1. Risk: platform branching leaks into core.
Control: keep all OS branching inside JVM interface implementations.
2. Risk: incomplete cleanup on failure.
Control: add rollback contract and tests for every create/apply step.

## Implementation Notes

1. Added `PlatformInterfaceFactory` (`expect/actual`) and `VpnPeerStats`.
2. Added JVM interface layer:
- `JvmVpnInterface`
- `InterfaceCommandExecutor` boundary (daemon-backed executor planned; current default factory uses in-memory executor)
3. Added JVM packet I/O adapters:
- `KtorDatagramUdpPort` (in `commonMain`) with timeout support for non-blocking poll behavior
4. Added reusable common test fakes (`InMemoryTunPort`, `InMemoryUdpPort`, `ManualPeriodicTicker`) and new JVM tests for idempotency, rollback, and adapter behavior.
