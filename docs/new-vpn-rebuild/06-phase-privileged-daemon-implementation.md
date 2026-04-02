# Phase 06: Privileged Daemon Implementation

## Objective

Implement a hardened JVM daemon subprocess that executes only an allowlisted command set.

## Implementation Status

Status: Not started (scaffold available)  
As Of: 2026-04-02

## Current Baseline in Code

1. `:new-vpn-daemon-jvm` starts a kRPC server and exposes `DaemonProcessApi` on `/services`.
2. `ping()` returns `DaemonCommandResult.Success(PingResponse)`.
3. All privileged command methods currently return `DaemonCommandResult.Failure(kind = UNKNOWN_COMMAND, ...)`.
4. No OS command runner, command allowlist implementation, payload validators, or audit logging pipeline is implemented yet.

## Scope (Pending)

1. Build daemon executable module with real privileged command handlers.
2. Implement command dispatcher from typed protocol commands.
3. Enforce strict command validation and execution model.
4. Keep daemon responsibilities limited to privileged OS control-plane commands.

## Work Breakdown (Pending)

1. Build `CommandDispatcher` mapping protocol methods to explicit handlers.
2. Build `PrivilegedCommandRunner`:
- executes binary + argv list only
- never uses shell interpolation
- captures stdout/stderr/exit code for structured internal diagnostics
3. Implement per-command validators:
- interface names
- CIDR/IP values
- ports/MTU ranges
4. Implement allowlist command catalog:
- interface create/delete/up/down
- address/route operations
- DNS apply/unset
- WireGuard apply/sync/remove peer
5. Add audit logging for every executed privileged command.
6. Enforce non-goals in code and tests:
- daemon must not implement TUN/UDP packet relay loop
- daemon must not perform runtime encryption/decryption loop

## Deliverables

1. `:new-vpn-daemon-jvm` runnable artifact with real command handlers.
2. Command allowlist and validators with tests.
3. Integration tests against client from phase 05.

## Exit Criteria

1. Daemon accepts only known typed commands.
2. Invalid payloads fail before process execution.
3. No code path allows arbitrary command execution.
4. Logs contain traceable request context and command outcome.
5. No daemon code path owns runtime packet forwarding.

## Risks and Controls

1. Risk: privilege escalation bug in handler mapping.  
Control: explicit one-command-one-handler mapping with tests.
2. Risk: command behavior differences across OS.  
Control: isolate OS-specific command packs and test per platform target.
