# Phase 01 Architecture Review Checklist

Use this checklist in PR review to prevent cross-module leakage before phase 02.

## Module ownership checklist

- [ ] `:new-vpn` contains orchestration/core contracts, session runtime, and common packet-loop code only (no daemon executable/runtime dependency).
- [ ] `:new-vpn-daemon-protocol` contains only typed protocol models.
- [ ] `:new-vpn-daemon-jvm` contains daemon runtime scaffolding only.
- [ ] `:new-vpn-daemon-client-jvm` contains client transport scaffolding only.

## Dependency checklist

- [ ] `:new-vpn` has no dependency on `:new-vpn-daemon-jvm`.
- [ ] `:new-vpn-daemon-jvm` depends only on `:new-vpn-daemon-protocol`.
- [ ] `:new-vpn-daemon-client-jvm` depends only on `:new-vpn-daemon-protocol`.

## Package boundary checklist

- [ ] Session concerns live under `com.rafambn.kmpvpn.session`.
- [ ] Interface concerns live under `com.rafambn.kmpvpn.iface`.
- [ ] Protocol models live under `com.rafambn.kmpvpn.daemon.protocol`.
- [ ] Daemon runtime code lives under `com.rafambn.kmpvpn.daemon`.

## Automation

- [ ] Run `./gradlew checkArchitectureBoundaries`.
- [ ] Run `./gradlew ciPhase01`.
