# ADR-033: Guarded Writes, Unique Backstops and Expiring Reports

## Status
Accepted. Same mechanics as the capacity registry (capacity ADR-033).

## Decision
- Every change to a licence is **one atomic update** guarded by the status that was loaded, which also appends the audit entry; the loser of a race gets a 409 (`ERR-LIC-00409`) and the trail never records a change that did not happen.
- **Unique indexes are the atomic backstops**: one licence per `(organisationId, product)`, one report per `(organisationId, licenseId, measuredAt)`. The use case does not look first.
- The latest count only moves forward: the update is guarded by "no count yet, or an older one", so it does not matter in which order, or how many times, the reports arrive; a retry after a crash between storing the report and folding it repairs the licence.
- A list index serves the newest reports of a licence; a **TTL index** on `measuredAt` expires reports after the retention (default 400 days).

## Consequences
- Positive: no lost update, no duplicate under concurrency, no lock; the store does not grow forever.
- Negative: a caller that loses a race must read again and retry; reports older than the retention are gone.
