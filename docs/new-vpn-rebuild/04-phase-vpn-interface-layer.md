# Phase 04: VpnInterface Layer

## Objective

Implement OS interaction behind a dedicated `VpnInterface` boundary.

## Scope

1. Build interface lifecycle abstraction independent of session code.
2. Provide JVM implementation that can run with real or stub executors.
3. Remove interface concerns from `VpnAdapter` semantics.

## Work Breakdown

1. Define `VpnInterfaceFactory` (expect/actual or JVM-only first).
2. Implement `JvmVpnInterface` with operations:
- create/check/delete interface
- up/down state
- apply MTU/address/routes/DNS
- read interface information and peer stats
3. Add `InterfaceCommandExecutor` boundary so daemon migration can plug in later.
4. Implement fake in-memory interface for fast `commonTest`.
5. Map platform-specific constraints (Linux/MacOS/Windows) in dedicated adapters.

## Deliverables

1. Core `VpnInterface` contract and factory.
2. JVM interface implementation wired to executor abstraction.
3. Tests validating idempotency and rollback behavior.

## Exit Criteria

1. `Vpn` interface lifecycle can run without `VpnAdapter`.
2. Session lifecycle and interface lifecycle are independent modules/classes.
3. Existing minimal API remains callable via orchestrator.

## Risks and Controls

1. Risk: platform branching leaks into core.
Control: keep all OS branching inside JVM interface implementations.
2. Risk: incomplete cleanup on failure.
Control: add rollback contract and tests for every create/apply step.
