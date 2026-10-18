# ADR-030: The Tool Counts, the Registry Keeps the Counts

## Status
Accepted. Same shape as the capacity registry (capacity ADR-030).

## Context
Journey 15 needs to know whether the organisation uses more software seats than it bought. Who uses each seat is what an inventory or SaaS admin tool knows; the user chose that the tool reports a **count**, not a list of assignments.

## Decision
- **The registry only records.** A `License` is what was bought: the product (unique per organisation), the `unit` a seat is counted in (`USER`, `DEVICE`, `INSTALL`, `CORE`, `OTHER`), how many seats (`entitled`, 1 to 10 million), the use at which to warn (`warnPercent`, 1 to 100) and how often the tool is expected to report (1 to 720 hours). It may name the vendor and the agreement it was bought under (vendor registry ids, **not checked**: the two registries stay independent).
- **A usage report is a fact**: the number of seats in use (`inUse`, zero or more) and the moment it was counted. **A count, never a list**: no user name, e-mail, device name or host is ever sent or kept, so the registry holds no personal data. Never edited, never deleted by hand; reports expire after `thinklab.license-registry.report-retention-days` (default 400).
- **Reporting is idempotent**: the same licence and moment is the same report (unique index on `(organisationId, licenseId, measuredAt)`, cut to the millisecond). A repeat answers 200 with the stored report; a new one 201.
- **The tool's clock may be a little off**: a count may be at most 5 minutes after the registry's clock.
- **The licence keeps the latest count** (value and moment), only ever moving forward in time, so it can be judged without reading the reports.

## Consequences
- Positive: no credentials, no access to the tools, no personal data; any tool that can send a number can report.
- Negative: the registry trusts the count; it cannot say who is over the limit, only that the licence is.
