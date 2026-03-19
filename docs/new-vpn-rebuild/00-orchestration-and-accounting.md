# New VPN Rebuild: Orchestration and Accounting

## Mission

Build a new architecture in `:new-vpn` where:

- `Vpn` is only the orchestrator.
- `SessionManager` owns transport/session lifecycle.
- `VpnInterface` owns OS interface lifecycle.
- JVM privileged operations run in a dedicated subprocess daemon.

This plan assumes the current baseline in `new-vpn`:

- Kotlin: minimal in-memory `Vpn`, `VpnAdapter`, and configuration models.
- Rust: working `TunnelSession` UniFFI binding with packet operations.

## Target Module Topology

1. `:new-vpn`
Purpose: KMP core API and orchestration (`Vpn`, `SessionManager`, `VpnInterface` contracts).
2. `:new-vpn-daemon-protocol`
Purpose: typed command/request/response models shared by daemon and JVM client.
3. `:new-vpn-daemon-jvm`
Purpose: privileged daemon executable with strict allowlist execution.
4. `:new-vpn-daemon-client-jvm` (optional; can stay in `:new-vpn` jvmMain initially)
Purpose: stdio IPC client used by JVM `VpnInterface` implementation.

## Phase Map

1. Phase 01: Foundation and module scaffolding.
2. Phase 02: Domain model and contract design.
3. Phase 03: SessionManager and transport strategy.
4. Phase 04: VpnInterface abstraction and JVM implementations.
5. Phase 05: Daemon protocol and subprocess IPC.
6. Phase 06: Privileged daemon implementation.
7. Phase 07: Vpn orchestrator integration and lifecycle semantics.
8. Phase 08: Security/observability/performance hardening.
9. Phase 09: Migration cutover, docs, and release.

## Dependency Rules

1. `:new-vpn` must not depend on daemon executable module.
2. `:new-vpn-daemon-jvm` depends on `:new-vpn-daemon-protocol` only.
3. JVM client depends on `:new-vpn-daemon-protocol`.
4. Rust tunnel binding stays in `:new-vpn` until a dedicated transport native module is needed.

## Accounting Model

Tracking unit: `Effort Point (EP)` where 1 EP ~= 0.5 engineering day.

Planned EP by phase:

1. Phase 01: 8 EP
2. Phase 02: 10 EP
3. Phase 03: 14 EP
4. Phase 04: 16 EP
5. Phase 05: 12 EP
6. Phase 06: 16 EP
7. Phase 07: 12 EP
8. Phase 08: 14 EP
9. Phase 09: 8 EP

Total: `110 EP`

## Progress Ledger

Use this table as the source of truth during execution.

| Phase | Status | Planned EP | Spent EP | Start Date | End Date | Gate Result | Notes |
|---|---|---:|---:|---|---|---|---|
| 01 | Completed | 8 | 8 | 2026-03-19 | 2026-03-19 | Passed | Module scaffolding, architecture checks, and CI entry tasks added |
| 02 | Not started | 10 | 0 | - | - | - | - |
| 03 | Not started | 14 | 0 | - | - | - | - |
| 04 | Not started | 16 | 0 | - | - | - | - |
| 05 | Not started | 12 | 0 | - | - | - | - |
| 06 | Not started | 16 | 0 | - | - | - | - |
| 07 | Not started | 12 | 0 | - | - | - | - |
| 08 | Not started | 14 | 0 | - | - | - | - |
| 09 | Not started | 8 | 0 | - | - | - | - |

## Stage Gates

Each phase exits only if all gate criteria pass:

1. `Scope Gate`: all listed deliverables merged.
2. `Quality Gate`: tests and static checks pass.
3. `Contract Gate`: public APIs reviewed and frozen for the next phase.
4. `Security Gate` (phases 05+): threat checks completed for added attack surface.

## Risk Register

| ID | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | Re-coupling core and platform layers | High | Enforce dependency rules in build files and code reviews |
| R2 | Daemon command injection | Critical | Typed protocol + allowlist + no shell execution |
| R3 | Incomplete lifecycle rollback | High | Explicit rollback paths and integration tests |
| R4 | Rust/Kotlin mismatch in session semantics | High | Session conformance tests around UniFFI wrapper |
| R5 | Scope drift | Medium | Gate phases strictly and use this accounting file as authority |

## Decision Log Template

Record architecture decisions in this file as appended entries.

- Decision ID:
- Date:
- Context:
- Decision:
- Consequence:

## Definition of Done (Program Level)

1. `Vpn` orchestrates `SessionManager` and `VpnInterface` only.
2. JVM privileged actions run only through daemon subprocess protocol.
3. No generic command execution path exists.
4. Core module can be tested with fake interface/session dependencies.
5. End-to-end lifecycle tests pass: `create`, `start`, `stop`, `delete`, peer updates, failure rollback.
