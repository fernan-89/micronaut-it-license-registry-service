# ADR-031: A Licence Is Judged When Read

## Status
Accepted. Same principle as the other registries (capacity ADR-031, vendor ADR-031), without a forecast.

## Decision
- **Nothing of the judgment is stored.** Every read of a licence judges it at that moment from its latest count:
  - `PAUSED`: not judged;
  - `NO_DATA`: no count reported yet;
  - `OVER_LIMIT`: more seats in use than bought;
  - `NEAR_LIMIT`: from `warnPercent` of the seats bought on (the limit itself, all seats in use, included);
  - `OK`: below that.
- `usagePercent` (in use / bought), `available` (bought minus in use, **negative when over**) and `stale` (the tool silent for more than twice its expected interval: a stale count is still judged, because seats in use do not free themselves).
- **No forecast.** Licences are bought in steps and seats move with people; a straight line through counts would mislead more than help (the capacity registry keeps its forecast for things that fill up).
- The list filters by `status`, `unit`, `vendorId` and `level`; the level is judged when read, so it is filtered in memory over the tenant's licences, oldest first.

## Consequences
- Positive: it cannot go stale; a later alerting rule can ask "what is over its limit" without a job keeping a flag true.
- Negative: no history of past verdicts (the usage reports are the history of counts).
