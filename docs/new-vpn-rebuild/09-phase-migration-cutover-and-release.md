# Phase 09: Migration Cutover and Release

## Objective

Complete cutover to the new architecture and ship a stable release.

## Scope

1. Final API and behavior validation.
2. Consumer migration docs and examples.
3. Release preparation and versioning.

## Work Breakdown

1. Write migration guide from old flows to new architecture in `new-vpn`.
2. Update examples to show daemon subprocess lifecycle.
3. Run full matrix tests:
- unit
- integration
- daemon protocol compatibility
- lifecycle end-to-end
4. Final cleanup:
- remove dead code paths
- remove temporary bridges no longer needed
5. Produce release checklist and tag candidate build.

## Deliverables

1. Published docs: architecture overview, migration guide, operational guide.
2. Release candidate artifact set for core and daemon modules.
3. Signed-off cutover report in orchestration/accounting file.

## Exit Criteria

1. All phase gates from 01 to 08 are satisfied.
2. Release checklist is fully complete.
3. Consumers can run complete lifecycle with documented setup.

## Risks and Controls

1. Risk: breaking consumer integrations on cutover.
Control: provide explicit migration steps and compatibility notes.
2. Risk: incomplete operational docs for daemon deployment.
Control: include startup, privileges, and troubleshooting runbooks.
