# VPN Architecture Migration Plan

## Goal

Create a clear separation where:

- `Vpn` is the single orchestrator.
- `SessionManager` owns transport/session lifecycle.
- `VpnInterface` owns OS interface lifecycle and metadata.
- JVM uses a privileged `Daemon` to run a restricted command set.

This plan is based on the current coupling in:

- `Vpn.kt` (orchestration + per-peer session map + adapter resolution)
- `VpnAdapter.kt` (mixed interface control + configuration + info)
- `PlatformService.kt` and desktop subclasses (OS operations + transport-adjacent logic)

## Target Architecture

### Layer 1: Public API

- `Vpn`
  - owns `engine`, `VpnConfiguration`, and alert callback
  - coordinates `SessionManager` and `VpnInterface`
  - exposes lifecycle methods: `create`, `start`, `stop`, `delete`, `exists`, `isRunning`, `information`

### Layer 2: Domain Orchestration

- `SessionManager`
  - accepts `engine`, peer list, and runtime transport config
  - creates/reconciles/closes peer sessions
  - decides transport strategy based on engine + peer count + config flags
  - no direct OS command execution

- `TransportStrategy` (selected by `SessionManager`)
  - examples:
  - `SinglePeerTransportStrategy`
  - `MultiPeerTransportStrategy`
  - `QuicTransportStrategy` (placeholder until QUIC implementation exists)

### Layer 3: Platform Boundary

- `VpnInterface`
  - pure interface contract for OS interaction:
  - `exists`, `createIfMissing`, `up`, `down`, `delete`, `isUp`, `configure`, `readInformation`
  - no session encryption/decryption responsibility

- `VpnInterfaceFactory` (expect/actual)
  - resolves platform implementation by OS and engine

### Layer 4: Privileged Execution (JVM only)

- `PrivilegedCommandDaemon` (separate process)
  - runs only allowlisted commands (no generic shell)
  - receives typed command requests from JVM client
  - executes privileged operations and returns structured result

- `PrivilegedCommandClient`
  - used by `JvmVpnInterface` implementation
  - communication channel: Unix domain socket/named pipe/TCP localhost
  - authentication: shared token and strict file permissions

## Proposed Package Layout

### `commonMain`

- `com.rafambn.kmpvpn.Vpn`
- `com.rafambn.kmpvpn.session.SessionManager`
- `com.rafambn.kmpvpn.session.TransportStrategy`
- `com.rafambn.kmpvpn.platform.iface.VpnInterface`
- `com.rafambn.kmpvpn.platform.iface.VpnInterfaceFactory`
- `com.rafambn.kmpvpn.platform.iface.VpnInterfaceInformation` (reuse existing info model where possible)

### `jvmMain`

- `com.rafambn.kmpvpn.platform.iface.jvm.JvmVpnInterface`
- `com.rafambn.kmpvpn.platform.iface.jvm.JvmVpnInterfaceFactory`
- `com.rafambn.kmpvpn.daemon.PrivilegedCommand`
- `com.rafambn.kmpvpn.daemon.PrivilegedCommandDaemon`
- `com.rafambn.kmpvpn.daemon.PrivilegedCommandClient`
- `com.rafambn.kmpvpn.daemon.CommandResult`

## Responsibility Matrix

- `Vpn`
  - orchestrates only
  - no per-peer session algorithm details
  - no direct OS command usage

- `SessionManager`
  - owns `MutableMap<String, ManagedVpnSession>` currently inside `Vpn.kt`
  - validates peer uniqueness
  - handles `ensure/reconcile/close` session flows

- `VpnInterface`
  - replaces direct `VpnAdapter` interface lifecycle responsibilities
  - handles native interface existence and state transitions

- `Daemon` (JVM)
  - executes privileged actions only from typed commands:
  - interface create/delete/up/down
  - route add/delete
  - DNS set/unset
  - wg set/sync/apply

## Migration Strategy

### Phase 0: Stabilize Baseline

1. Freeze current behavior with regression tests around:
2. `Vpn.start/stop/delete/exists/isRunning`
3. peer session reconciliation behavior
4. adapter existence resolution

Deliverable:
- baseline tests documenting current semantics before refactor.

### Phase 1: Extract SessionManager (No API Break)

1. Create `SessionManager` in `commonMain`.
2. Move from `Vpn.kt` to `SessionManager`:
3. `tunnelSessions` map and `ManagedVpnSession`
4. `ensureTunnelSessions`, `reconcileTunnelSessions`, `closeTunnelSessions`, `closeTunnelSession`
5. peer duplicate validation and `sessionIndexFor`
6. `Vpn` delegates session operations to `SessionManager`.

Deliverable:
- `Vpn` still public-facing, but session logic removed from it.

### Phase 2: Introduce VpnInterface Abstraction

1. Add `VpnInterface` contract.
2. Create adapter-backed implementation first:
3. `AdapterBackedVpnInterface(private val adapter: VpnAdapter, private val service: PlatformService<*>)`
4. Make `Vpn` depend on `VpnInterface` instead of raw `VpnAdapter`.
5. Keep `PlatformService` as temporary backend during transition.

Deliverable:
- stable abstraction boundary without rewriting platform logic in one step.

### Phase 3: Decouple from PlatformService

1. Create `VpnInterfaceFactory` and migrate `createPlatformService(engine)` call sites.
2. Move interface-specific methods out of `PlatformService` into `VpnInterface` implementations.
3. Keep a `LegacyPlatformServiceBridge` only for methods not migrated yet.

Deliverable:
- `Vpn` -> `VpnInterfaceFactory` + `SessionManager`, no direct `PlatformService` dependency.

### Phase 4: JVM Daemon Integration

1. Define typed commands with sealed classes:
2. `CreateInterface`, `DeleteInterface`, `SetAddress`, `SetMtu`, `WgSetConf`, `RouteAdd`, `RouteDel`, `DnsSet`, `DnsUnset`.
3. Implement daemon allowlist mapping command types to exact executables/args.
4. Build `PrivilegedCommandClient` and wire `JvmVpnInterface` to use it.
5. Add security controls:
6. drop generic command execution
7. input validation for interface names and addresses
8. token auth + strict socket/file permissions
9. audit log for privileged operations

Deliverable:
- all JVM privileged operations routed through daemon client.

### Phase 5: Transport Strategy Expansion

1. Add `TransportStrategy` selection inside `SessionManager`.
2. Rules:
3. `BORINGTUN + 1 peer` -> single-peer strategy
4. `BORINGTUN + N peers` -> multi-peer strategy
5. `QUIC` -> explicit unsupported/placeholder strategy until ready
6. Keep `createVpnSession(...)` as low-level factory used by strategies.

Deliverable:
- transport selection no longer spread across `Vpn` and session factory call sites.

### Phase 6: Remove Legacy Types

1. Remove/replace remaining `VpnAdapter` usage from public and internal paths.
2. Decommission or slim `PlatformService` into narrowly scoped provider interfaces.
3. Rename legacy terms where needed:
4. `VpnAddress` -> keep as value/handle type inside `VpnInterface`, not as orchestration dependency.

Deliverable:
- clear architecture boundaries with no blended adapter/service responsibilities.

## Suggested Public API Shape (End State)

```kotlin
class Vpn(
    private val engine: Engine,
    private val config: VpnConfiguration,
    private val vpnInterface: VpnInterface,
    private val sessionManager: SessionManager,
    private val onAlert: ((Pair<ErrorCode, String>) -> Unit)? = null
) : AutoCloseable {
    fun exists(): Boolean
    fun isRunning(): Boolean
    fun create()
    fun start()
    fun stop()
    fun delete()
    fun information(): VpnInterfaceInformation?
    override fun close()
}
```

## Backward Compatibility

1. Keep `Vpn` constructor overloads that build defaults (`VpnInterfaceFactory`, `SessionManager`) internally.
2. Preserve current error codes and alert behavior to avoid breaking consumers.

## Testing Plan

1. `commonTest`
2. `SessionManager` unit tests for peer add/remove/reconfigure and stale session cleanup.
3. `Vpn` orchestration tests with fake `VpnInterface` + fake `SessionManager`.
4. `jvmTest`
5. daemon client/daemon protocol tests
6. allowlist enforcement tests (reject non-typed commands, reject invalid iface names)
7. integration smoke tests for create/start/stop/delete against stubbed privileged executor.

## Rollout Checklist

1. Phase 1 and 2 merged with no public API break.
2. Phase 3 bridge complete.
3. Phase 4 daemon path enabled behind feature flag.
4. Feature flag becomes default after test hardening.
5. Remove legacy path after one stable release cycle.
