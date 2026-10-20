# ADR-034: The Promises Contract

## Status
Accepted. Implements platform ADR-001 (`thinklab-platform/docs/adr/001-the-promises-contract.md`).

## Context
The alerting service used to know this registry's fields and verdicts (which states are a broken promise, and the code of each). A rule that lived in a consumer was untested with the domain and had to be kept in step by hand.

## Decision
- The registry answers `GET /it-license-registry/v1/promises/retrieve?changedWithinDays=&limit=` as the platform contract says: every broken promise and every one that changed within the window (30 days unless asked, 1 to 365), at most `limit` of each (500 unless asked, 1 to 1000), broken first.
- **The judgement lives here:** a licence is **broken** (`LICENSE_OVER_LIMIT`) when more seats are in use than were bought, **kept** when OK or only near the limit (a notice on the screen, not an incident), and **not judged** when paused or without a count yet. The name is the product; a licence has no asset; the status is the licence's.
- **`changedAt`:** the later of the licence's last update and the moment its latest count was measured.
- Staff only, tenant-scoped, read-only.

## Consequences
- Positive: the rule is judged and tested with the domain; the consumer needs no knowledge of this registry.
- Negative: a count reported late about an old moment will not move `changedAt` forward; the licence is still listed while it is broken.
