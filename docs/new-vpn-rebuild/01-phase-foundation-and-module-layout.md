# Phase 01: Foundation and Module Layout

## Objective

Establish a clean project structure for a full rebuild, using `:new-vpn` as the core starting point.

## Implementation Status

Status: Completed  
Date: 2026-03-19

## Inputs from Current Baseline

1. `new-vpn/src/commonMain/kotlin/com/rafambn/kmpvpn/Vpn.kt` (orchestrator facade)
2. `new-vpn/src/commonMain/kotlin/com/rafambn/kmpvpn/iface/VpnInterface.kt` (merged interface/configuration contract)
3. `new-vpn/src/commonMain/rust/lib.rs` (working tunnel bindings)
4. `settings.gradle.kts` already includes `:new-vpn`

## Scope

1. Add new modules for protocol and daemon executable.
2. Define package boundaries and ownership.
3. Set project conventions for API visibility and testing.
4. Keep behavior unchanged in this phase.

## Work Breakdown

1. Add modules:
- `:new-vpn-daemon-protocol`
- `:new-vpn-daemon-jvm`
- optional `:new-vpn-daemon-client-jvm`
2. Configure Gradle:
- JVM toolchain alignment
- consistent Kotlin options
- test tasks and dependency constraints
3. Establish package roots:
- `com.rafambn.kmpvpn.session`
- `com.rafambn.kmpvpn.iface`
- `com.rafambn.kmpvpn.daemon.protocol`
- `com.rafambn.kmpvpn.daemon`
4. Introduce architecture lint rules (manual review checklist minimum, automated rules optional).
5. Add CI entry tasks for each module.

## Deliverables

1. Compiling module graph with no cyclic dependencies.
2. Readme section describing each module’s responsibility.
3. Placeholder test suites per module to validate wiring.

## Exit Criteria

1. `./gradlew build` succeeds for all new modules.
2. Dependency rule is enforced: core module does not depend on daemon executable.
3. No runtime behavior changes in `:new-vpn` yet.

## Risks and Controls

1. Risk: accidental cross-module leakage.
Control: fail build on forbidden dependencies.
2. Risk: setup churn blocks implementation.
Control: keep this phase strictly scaffolding-only.
