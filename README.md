# micronaut-it-license-registry-service

BIAN-aligned Service Domain **it-license-registry** (Control Record: `License`), port `8109`.

The second slice of Journey 15 (breadth): a **registry of software licences**. A `License` is what was bought: how many seats of a product,
counted in what (a user, a device, an install, a core). A `UsageReport` is what an inventory tool counts: how many seats are in use at a moment.
The registry says, **when you ask**, whether each licence is fine, near its limit or over it, and how many seats are left.

## What it guarantees, and what it does not

- **It only records** (ADR-030): the tool that counts reports each count (`usage/initiate`). The registry never scans, reads a tool or holds a
  credential. A licence may name its vendor and agreement (vendor registry ids, not checked).
- **A count, never a list** (ADR-030): a report is a number of seats in use and a moment; no user, e-mail, device name or host is kept, so the
  registry holds no personal data. Never edited, never deleted by hand; reports expire after `thinklab.license-registry.report-retention-days`
  (default 400). A count may be at most 5 minutes ahead of the registry's clock.
- **Reporting is idempotent** (ADR-030, ADR-033): the same licence and moment is the same report. A repeat, or 20 at once, answers the stored
  report with 200 (a new one is 201).
- **A licence is judged when read** (ADR-031), never stored: `level` is `OK`, `NEAR_LIMIT` (from `warnPercent` of the seats bought on, all
  seats in use included), `OVER_LIMIT` (more in use than bought), `NO_DATA` or `PAUSED`, with `usagePercent`, `available` (negative when over)
  and `stale` (the tool silent for more than twice its expected interval; a stale count is still judged). No forecast.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-LIC-00403`; another tenant's licence or report answers 404.
  There is no delete (a licence is paused).
- **Safe with several instances** (ADR-033): one licence per product and organisation, one report per `(licence, moment)`, every change guarded
  by the status loaded, and the latest count only moves forward in time whatever the order of the reports.
- **Not here yet:** who holds each seat, cost, true-up dates, telling someone when a licence goes over (a later pairing with alerting).

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call; `X-Executor` is mandatory on the actions (for a report, the name of the tool); `X-Role` is optional
and, with platform security on, comes from the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-license-registry/v1/initiate` `{"product":"Office 365 E3","unit":"USER","entitled":250,"warnPercent":90,"reportEveryHours":24,"vendorId":"<uuid, optional>","agreementId":"<uuid, optional>"}` (`unit`: USER, DEVICE, INSTALL, CORE, OTHER) |
| retrieve | `GET /it-license-registry/v1/{id}/retrieve` (judged now) |
| retrieve (collection) | `GET /it-license-registry/v1/retrieve?status=&unit=&vendorId=&level=` (oldest first) |
| update | `PUT /it-license-registry/v1/{id}/update` (the whole definition again, in any status) |
| control/pause | `PUT /it-license-registry/v1/{id}/control/pause` (ACTIVE -> PAUSED: no longer judged) |
| control/resume | `PUT /it-license-registry/v1/{id}/control/resume` (PAUSED -> ACTIVE) |
| audit-log/retrieve | `GET /it-license-registry/v1/{id}/audit-log/retrieve` |
| usage/initiate | `POST /it-license-registry/v1/usage/initiate` `{"licenseId":"<uuid>","inUse":231,"measuredAt":"<instant>"}` |
| usage/retrieve | `GET /it-license-registry/v1/usage/{id}/retrieve` |
| usage/retrieve (collection) | `GET /it-license-registry/v1/usage/retrieve?licenseId=&limit=` (newest first, 50 unless asked, at most 200) |

```text
licence: ACTIVE <-> PAUSED ; judged when read: NO_DATA / OK / NEAR_LIMIT / OVER_LIMIT
report:  a fact (a count at a moment), never changes, expires
```

```bash
curl -X POST "http://localhost:8109/it-license-registry/v1/usage/initiate" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: inventory-agent" \
  -H "Content-Type: application/json" \
  -d '{"licenseId":"<licenseId>","inUse":231,"measuredAt":"2026-10-18T02:00:00Z"}'
```

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-LIC-00403` | 403 | A REQUESTER used the licence registry (ADR-032) |
| `ERR-LIC-00404` | 404 | Licence or report not found (another tenant's answers the same) |
| `ERR-LIC-00409` | 409 | A second licence for the same product, illegal transition (pause a paused licence), or it changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure, seats out of range, a negative count, a count from the future, a moment that cannot be read |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Configuration

`HASH_SERVICE_URL` (default `http://localhost:8080`, the sovereign ids), `MICRONAUT_SERVER_PORT` (default 8109), `mongodb.uri`,
`thinklab.mongo.create-indexes` (default on), `THINKLAB_LICENSE_REPORT_RETENTION_DAYS` (default 400). The service calls no other service and
has no scheduler.

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 the tool counts, the registry keeps the counts · 031 a licence is judged when read · 032 staff only · 033 guarded writes, unique backstops and expiring reports.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
