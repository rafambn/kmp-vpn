# Phase 05: Daemon Protocol and Local RPC IPC

## Objective

Create the communication contract between JVM process and privileged daemon using a local RPC transport.

## Implementation Status

Status: Completed  
Start Date: 2026-03-21  
End Date: 2026-04-02

## Scope

1. Build typed request/response protocol in a shared module.
2. Implement shared kRPC contract and local Ktor WebSocket IPC transport.
3. Establish protocol-level failure taxonomy and timeout behavior.
4. Keep protocol strictly control-plane (no runtime packet forwarding payloads).

## Protocol Design (Implemented)

1. Transport: kRPC over Ktor WebSocket route `/services`, default client endpoint `ws://127.0.0.1:<port>/services`.
2. Service contract: `DaemonProcessApi` (`@Rpc`) with one method per control-plane command.
3. Request model:
- primitive method parameters for most commands
- structured `ApplyPeerConfigurationRequest` + `PeerRequest` for high-cardinality peer configuration
4. Response model:
- `DaemonCommandResult.Success(data)`
- `DaemonCommandResult.Failure(kind, message)`
5. Error taxonomy: `DaemonErrorKind` (`UNKNOWN_COMMAND`, `MALFORMED_PAYLOAD`, `VERSION_MISMATCH`, `VALIDATION_ERROR`, `INTERNAL_ERROR`).
6. Handshake: zero-argument `ping()` returning singleton `PingResponse`.
7. Explicitly not implemented in protocol yet:
- per-request `id` envelope field
- protocol `version` field in command payloads
- stdout/stderr passthrough fields

## Work Breakdown

1. Create `:new-vpn-daemon-protocol` with typed requests/responses and result taxonomy.
2. Add `@Rpc` interface (`DaemonProcessApi`) shared by daemon and client.
3. Implement protocol serialization smoke tests and malformed-payload checks.
4. Implement JVM kRPC client (`DaemonProcessClient`) with timeout handling and handshake validation.
5. Add daemon kRPC server scaffold (`/services`) and register `DaemonProcessApiImpl`.
6. Validate control-plane-only constraint in tests and model naming.

## Deliverables

1. Stable protocol package and smoke tests.
2. kRPC JVM client that performs handshake and typed round-trips against a mock endpoint.
3. kRPC daemon server scaffold wired to protocol handler.
4. Shared failure taxonomy for daemon responses.

## Exit Criteria

1. Client executes typed command round-trips against mock daemon over local kRPC transport.
2. Malformed payload deserialization fails predictably.
3. Protocol contains only control-plane operations.
4. Handshake behavior is deterministic (`ping()` success required).

## Risks and Controls

1. Risk: protocol drift between daemon and client.  
Control: contract/smoke tests in protocol, client, and daemon modules.
2. Risk: local endpoint exposure beyond intended scope.  
Control: bind to `127.0.0.1` by default; remote exposure requires explicit opt-in and separate auth controls.
3. Risk: hidden command injection vectors.  
Control: no raw command string field in protocol.

## Implementation Notes

1. `DaemonProcessClient` includes timeout guards and throws `DaemonClientException.Timeout` or `ProtocolViolation` for handshake failures.
2. `DaemonProcessApiImpl` currently implements only `ping()` success; all other commands intentionally return `UNKNOWN_COMMAND` scaffold failures.
3. Both daemon and client currently use JSON serialization, with TODO markers to migrate to Protobuf later.
