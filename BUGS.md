# Repository bug scan — 2026-07-02

Findings from a full-repository correctness review (modules: `raptor`, `gtfs`, `rail`,
`server`, `raptor-oba-bridge`, `raptor-benchmarks`, plus build/CI/Docker/CloudFormation).
Every finding below was verified against the code at `9f8e702`. Ranked most severe first
within each section. Status boxes track remediation.

---

## 1. Core routing correctness (`raptor`)

### 1.1 `RouteScanner` trip cursor goes stale and drops catchable trips — HIGH
- [x] Fixed — `RouteScanner.startRoute` resets the scan position at the start of every route traversal (upstream planarnetwork/raptor #52). Test: `RouteScannerResetTest`.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/RouteScanner.java:35-45`

The per-route scan position only ever moves toward earlier trips, and every later query
starts from it. That is only sound if successive queries on a route use non-increasing
times *at the queried stop*, but across rounds the algorithm queries the same route at
different stops with unrelated times. A query needing a trip at a higher index than the
cached position immediately hits `trip.departureTime(stopIndex) < time` and returns
`Optional.empty()`.

Concrete failure (one `scan()` call, one `RouteScanner` instance): route R stops
A(0), M(1), B(2), C(3); trips T1: A 08:00 / M 08:30 / B 09:00 / C 10:00 and
T2: A 12:00 / M 12:30 / B 13:00 / C 14:00. A second route reaches B at 08:30 in round 1;
round 2 scans R from B, boards T1 and caches cursor index 0; a footpath B→A (2 h) marks
A = 10:30. Round 3 scans R from A: the scan starts at cached index 0, sees
T1.dep(A) = 08:00 < 10:30, breaks, returns empty — T2 (dep A 12:00, catchable) is never
examined and the valid journey X→B, walk to A, T2 to M is permanently lost. Affects
`DepartAfterQuery` and every departure iteration of `RangeQuery` (scanner is shared
across a departure's rounds). Typical real-world trigger: a footpath back to an upstream
station reached in a later round.

### 1.2 Unguarded `transfersArray[-1]` crash for an origin missing from the stop index — HIGH
- [x] Fixed — `RaptorAlgorithm.toStopIndices` drops origins the index does not know, so the scan runs no rounds and returns no journeys (upstream planarnetwork/raptor #42). Test: `FootpathDestinationTest`.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/RaptorAlgorithm.java:273-274`
(same pattern at `:394-395`)

`stopToIndex.defaultReturnValue(-1)`; the absent-stop guard exists only for the transfer
*destination* (`:285`), not the marked stop itself. Round 1's marked set is the raw
origins map, so an origin with no service that day (absent from the per-date index)
reaches `transfersArray[-1]` and throws `ArrayIndexOutOfBoundsException` instead of
returning "no journeys".

HTTP surface: `GET /first-arrival?orig=SCA&dest=YRK&startDate=2025-05-01T08:00:00`
(a no-service date — the repo's own range-query test uses this date and expects an empty
200 — or any mistyped CRS code on a normal day) returns a 500 from
`server/src/main/java/com/joshuaharwood/velociraptor/server/RaptorController.java:237-243`.
The sibling `/` and `/detail` endpoints return `200 []` for identical inputs only by
accident (`RangeQuery.plan` pre-filters origin departures and returns early) —
inconsistent HTTP semantics plus a client-triggerable crash.

### 1.3 Transfer-only journeys get `departureTime = 0` and `arrivalTime = 0` — MEDIUM
- [x] Fixed — a journey made only of fixed links is no longer returned by either journey factory. Tests: `FootpathDestinationTest`, `RailJourneyFactoryFixedLinkOnlyTest`.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/result/JourneyFactory.java:145`
and `:163`

Both `getDepartureTime` and `getArrivalTime` fall through to `return 0` when the legs
contain no `TimetableLeg`. A direct footpath origin→destination *is* recorded at round 1
(`scanTransfers` → `kConnections[dest][1] = TransferLeg`), so such journeys are produced:

- `DepartAfterQuery`: plan(O, D, 09:00) with a 600 s footpath O→D yields
  `Journey([TransferLeg], departureTime=0, arrivalTime=0)` instead of (32400, 33000).
- `RangeQuery`: the leading transfer is anchored to `currentSearchTime` but arrival is
  still 0, giving a journey that "arrives" before it departs — one per iterated departure.

Anything sorting/Pareto-filtering on these values (e.g. `MultipleCriteriaFilter`)
operates on garbage.

### 1.4 Range-mode re-board discards a boardable held trip at drop-off-only stops — LOW (documented TS-parity deviation)
- [x] Fixed — both scan loops now gate boarding on the route's pick-up flag via `RouteScanner.canBoard`, and a lookup only ever replaces the held trip with an earlier one, so a held trip is never discarded. Test: `RaptorAlgorithmReboardTest`.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/RaptorAlgorithm.java:377-383`

Unlike `scanRoutes` (`:244`, which checks `trip.canBoard(pi)`), `scanRoutesRange`
re-boards whenever `previousArrival < trip.arrivalTime(pi) + interchange`. At a
pickup-forbidden stop with an earlier retained label, the held boardable trip is replaced
by one that can never satisfy the alight check, losing all downstream improvements of the
original trip in that round — range queries can miss journeys the plain `scan` finds.
Comments at `:341-342`/`:379-380` mark this as a deliberate 1-to-1 port of the TS reference, but it
is incorrect relative to RAPTOR.

---

### 1.5 Re-board test is a strict less-than, so a trip dwelling at a stop is never caught — LOW-MEDIUM
- [x] Fixed — the re-board step now follows the paper: it runs after (not instead of) the alight check, fires when the previous label is at or before the held trip's *departure*, and switches only to an earlier-indexed trip. Tests: `RaptorAlgorithmReboardTest`; `ScanOracleTest` no longer excludes the dwell-overlap pattern and draws dwells shorter than the interchange.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/RaptorAlgorithm.java`, the re-board
branch of both `scanRoutes` and `scanRoutesRange`: `previousArrival < trip.arrivalTime(pi) + interchange`.

When the previous round's label at a stop was set by the very trip now held (the trip was boarded
further upstream in this round), the label equals the held trip's arrival there and the strict test
never looks for another trip. A lower-indexed trip that arrived earlier and is still dwelling
departs after that label and, being earlier at every later call, arrives sooner downstream. The
scan keeps the held trip and the change is lost.

Found by `ScanOracleTest` (seed 1247 before the exclusion): origin S4 at 20137 boards trip16
(S4 21222 → S3 24636 → S2 25932). Trip0 sits at S3 from 24467 to 24755 and reaches S2 at 25767.
The two-trip journey via S3 is never produced; the destination gets only the 25932 arrival. The
correct rule allows equality: a label at or before the held trip's arrival means every trip
departing at or after the label is catchable, and the earliest of them is never worse than the
held trip. Doing that safely needs the lookup to switch only to an earlier-indexed trip (or keep
the held trip when the lookup fails), which is the same change as the documented failed-lookup
deviation in 1.4 and `RaptorAlgorithmReboardParityTest`. Inherited: the 0.2.0 TS baseline and
upstream planarnetwork/raptor 5.2.0 both use the strict test.

## 2. GTFS→RAPTOR conversion (`raptor-oba-bridge`)

All in `raptor-oba-bridge/src/main/java/com/joshuaharwood/velociraptor/obabridge/RaptorAlgorithmFactory.java`.

### 2.1 Interchange-time map construction broken three ways — HIGH (feed-dependent)
- [ ] Fixed

Lines 101-103: `dao.getAllTransfers().stream().collect(Collectors.toMap(t -> toStop.apply(t.getFromStop()), Transfer::getMinTransferTime))`

1. No merge function: any feed with two `transfers.txt` rows sharing a `from_stop_id`
   (routine — one row per destination stop) throws
   `IllegalStateException: Duplicate key` and aborts the whole build.
2. OBA's `Transfer.getMinTransferTime()` returns its missing-value sentinel (−999) when
   `min_transfer_time` is absent (typical for transfer_type 0/1/3 rows), producing a
   **negative** interchange time — `arrival + interchange` in
   `RaptorAlgorithm.scanTransfers` (`:295`) then lands earlier than the actual arrival:
   physically impossible journeys.
3. A row A→B's time is recorded as the same-station interchange at A regardless of
   `to_stop_id`. (Also NPEs on feeds using route-/trip-level transfers, where
   `getFromStop()` is null.)

### 2.2 Fixed links ignore day-of-week and date validity — MEDIUM
- [x] Fixed

Lines 96-99 build transfers from `dao.getAllFixedLinksByStop()` (all links), even though
`FixedLink` carries monday…sunday flags plus startDate/endDate and the DAO provides
`getFixedLinksRunningOnServiceDay(serviceDate)`. Only the time-of-day gate survives into
the raptor `Transfer`. Concrete failure: a Sunday-only replacement link is offered on a
Wednesday `serviceDate` query — the exact class of service-calendar filtering the factory
performs for trips (comment at line 77) is skipped for transfers.

### 2.3 Overtaking detection incomplete — MEDIUM
- [x] Fixed — `RouteGrouping` (shared by `RaptorAlgorithmFactory` and `TestRaptorBuilder`) compares arrival and departure at every call and opens as many routes per signature as the orderings need (upstream #51). Test: `RouteGroupingTest`.

`getRouteId`, lines 296-315. Two gaps, both interacting with `RouteScanner`'s
requirement of per-stop monotone times within a route bucket:

1. Only final-stop arrivals are compared. Trip A: dep S1 09:00, dep S2 10:00,
   arr S3 10:30; trip B: dep S1 09:30, dep S2 09:45 (faster), long dwell, arr S3 10:40.
   B doesn't beat A's final arrival, so both land in one bucket sorted [A, B]; at S2 the
   departure sequence is 10:00, 09:45 — non-monotone. A query touching S2 at 09:50 hits
   B first, the scanner breaks, and the catchable 10:00 departure on A is lost.
2. Trips diverted into the `…overtakes` bucket are never compared against that bucket
   (the loop reads only the base `routeId` list), so the overflow bucket itself can be
   non-FIFO — riders get boarded onto a slower trip (violates RAPTOR's guarantee).

### 2.4 Route signature has no delimiter → stop-pattern collisions — LOW (latent)
- [x] Fixed — `RouteGrouping` joins the per-stop entries with `,`.

Lines 297-301: the signature concatenates `stopId + boardFlag + alightFlag` per stop with
no separator, so numeric stop-id patterns collide: [stop "1", stop "11"] →
`"111"+"1111"` = `1111111`; [stop "11", stop "1"] → `"1111"+"111"` = `1111111`. Both
patterns merge into one "route" whose stop arrays come from whichever trip arrived first
— wrong boarding stops/times. Safe for the current fixed-width CRS codes; one feed swap
away from wrong answers. Fix: join with a delimiter.

### 2.5 Stops memoised without agency id — LOW (latent, multi-agency feeds)
- [ ] Fixed

Line 68 (`stopLookup`) keys by `obaStop.getId().getId()`, discarding the agency id
(trips likewise at line 53). Two agencies' distinct stops sharing an id alias to one
`Stop`, splicing unrelated networks together (phantom transfers).

---

## 3. Server (`server`)

### 3.1 Windows starting after midnight miss the previous service date's >24:00 departures — MEDIUM
- [ ] Fixed

`server/src/main/java/com/joshuaharwood/velociraptor/server/http/RaptorResource.java:40-43`
(also `:56-59`, `:71`) — the service date is always `startDate.toLocalDate()`, so only
that one date's timetable is scanned. Fixture trip 30214822 departs LST at 24:18 on
Saturday service dates (00:18 Sunday wall-clock; see comment at
`RaptorResourceTest.java:282-292`).
`GET /?orig=LST&dest=COL&startDate=2025-06-08T00:00:00&endDate=2025-06-08T01:00:00`
builds Sunday's algorithm only and returns `[]` despite a real 00:18 train inside the
window. The forward case (window crossing midnight ahead, `secondsSinceServiceMidnight`)
was explicitly fixed; the backward case was not.

### 3.2 Unbounded, client-controlled per-date algorithm cache — MEDIUM-LOW
- [ ] Fixed

`server/src/main/java/com/joshuaharwood/velociraptor/server/RaptorController.java:249-262`
— `algorithmCache.computeIfAbsent(serviceDate, …)` caches a `RaptorAlgorithm` forever
for every distinct requested date, with no validation against the feed validity window
and no eviction. Any parseable date (year 0001–9999) is accepted; each novel date also
runs a full `RaptorAlgorithmFactory.createFromDao` build. Trivial remote memory/CPU
exhaustion, and post-startup misses do slow scans during live traffic — exactly what the
`PrecomputeReadinessCheck` javadoc says must not happen.

### 3.3 Packaged default GTFS path is a repo-relative test resource — LOW
- [ ] Fixed

`server/src/main/resources/application.properties:5` —
`velociraptor.gtfs.source.path=src/test/resources/gtfs-sample.zip` is baked into the
production artifact and only resolves when the JVM is launched from the `server/` module
directory. A container started without `VELOCIRAPTOR_GTFS_SOURCE_PATH`
(cloudformation/velociraptor-app.yaml:206 is the only place it is set) dies at startup.
Should be `%dev`/`%test`-scoped.

### 3.4 `S3GtfsLoader` URI parsing bypasses its own validation — LOW
- [ ] Fixed

`server/src/main/java/com/joshuaharwood/velociraptor/server/S3GtfsLoader.java:64-66` —
`s3://bucket` (no key) makes `uri.getPath()` return `""` so `substring(1)` throws
`StringIndexOutOfBoundsException`; a bucket name that is not a valid hostname makes
`uri.getHost()` return null (NPE/confusing SDK error) instead of the intended
"Expected S3 URI in format s3://bucket/key" message. The temp file (line 72) is only
deleted on the success path (mitigated by `deleteOnExit`).

---

## 4. Data loading (`gtfs`, `rail`)

### 4.1 GTFS zip is never closed — MEDIUM (resource leak)
- [ ] Fixed

`gtfs/src/main/java/com/joshuaharwood/velociraptor/gtfs/GtfsDeserialiser.java:25` —
`GtfsReader.run()` closes only the entity store, never the input source; the `ZipFile`
is closed only by `CsvEntityReader.close()`, which is never called. `S3GtfsLoader`
downloads the feed to a temp file, loads it, then deletes the file — on Linux the
multi-hundred-MB zip's disk space and fd stay pinned for the process lifetime
(unlinked-but-open); on Windows the delete fails outright. Repeated loads (tests,
per-fork benchmark reloads) accumulate leaked handles. Fix: close the reader after
`run()`.

### 4.2 `Leg.FixedLink` times declared non-null but always constructed null — LOW
- [x] Fixed — `RailJourneyFactory.withFixedLinkTimes` fills them from the neighbouring train legs (after a train: its arrival plus the origin interchange; before the first train: an interchange before it departs, back by the duration). `JourneyFactory` now dates a leading link the same way instead of at the range seed. Tests: `RailJourneyFactoryFixedLinkTimesTest`, `RangeSeedingTest`.

`rail/src/main/java/com/joshuaharwood/velociraptor/rail/Leg.java:18-19` and
`rail/src/main/java/com/joshuaharwood/velociraptor/rail/RailJourneyFactory.java:125-126`
— the module is `@NullMarked` and the components carry no `@Nullable`, yet every
fixed-link leg is constructed with `null, null`. The comment above the construction
claims "times are filled in by a chronological post-pass once both neighbours are known"
— no such pass exists (and the comment at `:133-134` contradicts it). Any consumer that
trusts the declared type NPEs; the one existing consumer (`RaptorController.atLondon`)
survives only via a defensive null-check. Fix: annotate `@Nullable` and delete the stale
comment (or implement the pass).

### 4.3 `DashedServiceDateFieldMappingFactory.convert` rejects dashed dates — LOW (latent)
- [ ] Fixed

`gtfs/src/main/java/com/joshuaharwood/velociraptor/gtfs/DashedServiceDateFieldMappingFactory.java:60-69`
— `translateFromCSVToObject` strips dashes before `ServiceDate.parseString`, but the
`Converter.convert` implementation on the same mapping passes the raw value through, and
`parseString` only matches `YYYYMMDD`. `convert(ServiceDate.class, "2017-01-01")` throws
for exactly the input the read path accepts. Only reachable via OBA's
deferred-value/transformer machinery — latent, but internally inconsistent.

### 4.4 Stale/contradictory docs worth fixing alongside — INFO
- [ ] Fixed

- `RailJourneyFactory.java:94-97` vs `:150-152` — `railTripOf` is documented as
  unwrapping `OffsetTrip` wrappers but is a plain cast to the final record `RailTrip`;
  no `OffsetTrip` exists on this branch. The moment the multi-day overlay returns, every
  day ≥ 2 reconstruction throws `ClassCastException`.
- `ResultsFactory.getResults` javadoc (and `RailJourneyFactoryTest` class javadoc) call
  `currentSearchTime` a "lower bound"; both implementations correctly apply it as an
  *upper* bound (rRAPTOR de-dup for latest-to-earliest iteration). Documentation error
  only.

---

## 5. Deploy & tooling (each independently blocks a deploy)

### 5.1 CloudFormation template cannot deploy — BLOCKING
- [ ] Fixed

`cloudformation/velociraptor-app.yaml`:

1. `:80` — `TaskSecurityGroup` is `Type: AWS::IAM::SecurityGroup`, which is not a
   CloudFormation resource type (should be `AWS::EC2::SecurityGroup`). Stack creation
   fails immediately.
2. `:84-105` — even with the type fixed, `TaskSecurityGroup`'s inline ingress references
   `AlbSecurityGroup` while `AlbSecurityGroup`'s inline egress references
   `TaskSecurityGroup`: a circular dependency CloudFormation rejects. The
   cross-references must move to standalone `AWS::EC2::SecurityGroupIngress/Egress`
   resources.
3. `:95-108` (with `:88`) — `AlbSecurityGroup` is attached to nothing (the ALB is
   pre-existing; only listener ARNs are parameters). The task SG's sole ingress rule
   admits traffic only from this orphaned SG, so the real ALB's health checks can never
   reach port 8080 — every target stays unhealthy. The existing ALB's SG id must be a
   parameter.
4. `:130-134` — the S3 policy substitutes the full `GtfsSourceUri`
   (`s3://bucket/key`) into the ARN, producing `arn:aws:s3:::s3://bucket/key(/*)` —
   matches nothing → AccessDenied downloading GTFS → precompute never completes →
   readiness never passes. Bucket/key must be parsed out (and `ListBucket` should target
   the bucket, not the object).

### 5.2 `build-and-push.sh` always fails at the docker build — BLOCKING
- [ ] Fixed

`build-and-push.sh:31` — `docker build -t … .` runs at the repo root, where no file named
`Dockerfile` exists (only `Dockerfile.jvm`, `Dockerfile.native`,
`Dockerfile.native-micro`). Additionally all three Dockerfiles `COPY target/quarkus-app/…`
(or `target/*-runner`), assuming the build context is `server/`, so even `-f
Dockerfile.jvm` from the root fails on the COPY. Needs
`docker build -f Dockerfile.jvm server` (after `mvn package`).

### 5.3 Minor build/CI/runtime issues
- [ ] Fixed

- `server/pom.xml:148-149` — two sibling `<jvmArgs>` elements bind to a single String
  parameter; one of `--add-opens …` (quarkusio/quarkus#47769 workaround) or
  `-XX:+UseCompactObjectHeaders` is silently dropped. Must be one element.
- `Dockerfile.jvm:99` / `cloudformation/velociraptor-app.yaml:64` —
  `-XX:+ZGenerational` was obsoleted by JEP 490 (JDK 24): warning + ignored on the JDK 25
  base image, and its removal is queued (JDK-8369983 targets JDK 26) — a startup time
  bomb on the next base-image bump.
- `.github/workflows/maven.yml:14-15,35-36` — the `maven-dependency-submission-action`
  step (needs `contents: write`) also runs on `pull_request`; fork PR tokens are
  read-only, so the job fails. Gate it with `if: github.event_name == 'push'`.
- Root `pom.xml:9-22` — the comment claims "only `raptor` builds here" but five modules
  are listed; `raptor-benchmarks` is silently excluded from `<modules>` and does not
  compile against this branch's APIs (`createMultiDayOverlayFromDao`,
  `createFlattenedFromDao`, `RangeQuery.forJourney`, `GroupStationDepartAfterQuery`, and
  a 3-arg `DepartAfterQuery` constructor do not exist). Only `ScanBenchmark` and
  `FactoryBuildBenchmark` would compile — the fat benchmarks jar per its README cannot be
  built. Stale either way; needs the comment and/or module list reconciled.

---

## Refuted during verification

- **`@{argLine}` in `server/pom.xml:168/:189` breaks every `mvn test`** — refuted. No
  JaCoCo plugin defines the `argLine` property, but master CI is green on the current
  HEAD (`9f8e702`) running `mvn -B package` over the full reactor, so Surefire resolves
  the undefined late-binding reference harmlessly. (Still slightly untidy; harmless.)

## Checked and found sound (selection)

- `QueueFactory.fillQueue` earliest-stop selection; marked-stop bookkeeping +
  `bestArrivals` pruning; range-mode label retention and the dangling-connection
  back-walk (`RangeQueryDanglingPointerTest`); `MultipleCriteriaFilter` Pareto logic;
  `ScanResults` reuse within `RangeQuery`.
- DST/wall-clock conversion in `RaptorController.toOffset`/`atLondon` (pinned by
  `RaptorControllerTimeTest`); `secondsSinceServiceMidnight` forward cross-midnight
  window math; DTO field-mapping order; CDI initialization order and precompute/readiness
  interaction; thread-safety of the shared per-date `RaptorAlgorithm`.
- Over-24:00 GTFS time handling end-to-end (bridge passes raw seconds through;
  `toDateTime` rolls past midnight correctly); `FixedLink` OBA id generation /
  `equals`/`hashCode`; double-checked locking in `ExtendedGtfsRelationalDaoImpl`; UTF-8/BOM
  handling in csv-entities; `pickUpDropOffIsTrue` semantics (GTFS 0 = allowed); trip
  sort stability in `assemble`; the fat-jar workflow.

---

# Repository bug scan — 2026-09-10

Second full-repository review (all six modules plus build/deploy). Every finding was verified
against the code at `73dcae3`; the ones marked *live* were reproduced against the running server
loaded with `data/gtfs-2026-09-09.zip` (service date 2026-09-08). Items already in the 2026-07-02
scan above are not repeated. Ranked most severe first within each section.

## 6. Core routing correctness (`raptor`)

### 6.1 A not-via stop ends the route scan, discarding every boarding downstream of it — HIGH
- [x] Fixed — both route scans now drop the held trip at a not-via stop and carry on, so a marked stop beyond it can still board. Test: `NotViaTest`.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/RaptorAlgorithm.java:180-183`
(`scanRoutes`) and `:338-340` (`scanRoutesRange`), with `model/QueueFactory.java:36-41`.

The queue keeps only the *earliest* marked position per route, and the scan `break`s at a not-via
stop. A marked stop on the same route positioned after the forbidden one is never visited in that
round, so boarding there - which passes through nothing forbidden - is lost, and the stop is not
re-marked later.

Reproduced: route 1 O(dep 100) → A(200) → B(300); route 2 A(dep 210) → N(220) → B(320) → C(400);
not-via N; query O→C. Round 1 marks A and B; round 2 scans route 2 from A, boards, breaks at N and
never reaches B. Both `DepartAfterQuery` and `RangeQuery` return `[]`, although O→B on route 1 then
B→C on route 2 avoids N. Fix: at a not-via stop drop the held trip (`trip = null`, `tripIdx =
MAX_VALUE`, `boardingPoint = ABSENT_VAL`) and `continue`, so later positions can still board.

### 6.2 Range-mode footpaths ignore the retained-label bound and can raise it, so dominated journeys are returned — MEDIUM (live)
- [x] Fixed — `scanTransfersRange` now bounds a fixed link with `rangeBound`, the same retained-label and destination bound the range route scan uses. Tests: `RangeDominationTest` (the synthetic case plus a no-dominated-journey property over 2×2000 random timetables, which failed at seed 9 before the fix).

`RaptorAlgorithm.java`, `scanTransfersRange` (`:389-393`): the improvement test is `arrival <
results.getBestArrival(stopPi)`, where `bestArrivals` is reset for every departure. The route scan
in the same mode uses `results.rangeBound(stopPi)` - the retained `kArrivals` labels plus the
destination bound - which is the whole point of rRAPTOR label retention. Worse, `setTransfer`
(`model/ScanResults.java:107-109`) writes `currentRound[dest] = time` unconditionally, so a footpath
arrival later than the retained label *overwrites* it with a larger value, and the weakened bound
persists into the next (earlier) departures, which then record further dominated journeys. No
production path applies `MultipleCriteriaFilter`, so these reach HTTP clients.

Live: `GET /?orig=SDR&dest=MYB&startDate=2026-09-08T07:52:00&endDate=2026-09-08T08:31:00` returns,
alongside the direct 08:30→09:09 train, a four-leg journey SDR 07:52 → PRR → AYS → AMR 09:08 then
the 70-minute AMR→MYB fixed link, arriving 10:19 - a later departure is available that arrives
earlier with three fewer changes. The single-departure window `07:52-07:53` does not return it
(nothing retained, so `bestArrivals` holds the 09:09 and the walk is refused), nor does
`/first-arrival`. Synthetic reproduction: trips O 1000→W 1010, W 1015→Y 1030, O 800→Z 820,
O 600→V 700, V 700→Y 1110; footpath Z→Y 300 s; `plan(O, Y, 0, 2000)` returns 800→1120 and
600→1110 as well as 1000→1030. Fix: bound the transfer relaxation with `rangeBound(stopPi)` as
the route scan does (`currentRound[p] <= bestArrivals[p]` always holds, so this only tightens).

### 6.3 Range queries seed only the origin's own trip departures, so footpath-first journeys are missed — MEDIUM
- [x] Fixed — `RangeQuery` now also seeds, for each fixed link out of the origin, the far end's departures pushed back by the link and the far end's interchange (the paper's Ψ), unless a journey may not begin with a fixed link. Tests: `RangeSeedingTest`; `RangeDominationTest` gains a completeness property that range results cover every depart-after journey.

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/query/RangeQuery.java:50-53, 63-64`.
The iterated departure set is `departureTimesAtStop[origin]` filtered to the window. A journey that
starts by walking from the origin and boards elsewhere is found only if some origin trip departure
happens to fall early enough for the walk; the window start is never seeded, nor are the departure
times at footpath-reachable stops offset by the walk (RAPTOR 2012 §4.2's Ψ). Inherited from the TS
reference's range query.

Reproduced: trips O 830→X 1200, O 1000→X 1100, Y 930→X 1000; footpath O→Y 600 s.
`plan(O, X, 900, 1100)` returns only 1000→1100; leaving O at 900, walking to Y and boarding at 930
arrives 1000 - the best journey in the window - and is never produced. Fix: seed the window start
(and, for completeness, footpath-reachable stops' departures minus the walk) in the departure set.

### 6.4 `MultipleCriteriaFilter` keeps a dominated journey depending on input order — LOW (unused in production)
- [ ] Fixed

`raptor/src/main/java/com/joshuaharwood/velociraptor/raptor/result/filter/MultipleCriteriaFilter.java:59-63, 68-78`.
The sort key is departure ascending / arrival descending only, and `compare` looks at subsequent
journeys only, so two journeys tied on both times keep input order and the one with more legs is
dropped only if it sorts after the other. `[direct, change]` → both kept; `[change, direct]` → only
`direct`. Inherited from the TS filter. Fix: add leg count (descending) as a final sort key.

## 7. GTFS→RAPTOR conversion (`raptor-oba-bridge`, `gtfs`)

### 7.1 Request stops (`pickup_type`/`drop_off_type` = 3) are treated as no boarding / no alighting — HIGH (live)
- [x] Fixed — only type 1 forbids use. Test: `RequestStopTest`.

`raptor-oba-bridge/src/main/java/com/joshuaharwood/velociraptor/obabridge/RaptorAlgorithmFactory.java:304-306`
(`pickUpDropOffIsTrue(int val) { return val == 0; }`, used at `:289-290`).

GTFS: 0 = regular, 1 = none, 2 = phone the agency, 3 = coordinate with the driver. Only 1 forbids
use; the UK feed uses 3 for request stops. On 2026-09-08 the September feed has 7,826 stop-time
rows flagged 3/3 and **72 stations served only that way** (Umberleigh, King's Nympton, Yeoford, the
Heart of Wales and Far North lines, …). Each gets an empty route list and no trip can alight, so any
query from or to them is empty: `GET /?orig=UMB&dest=EXD&startDate=2026-09-08T12:00:00&endDate=2026-09-08T14:00:00`
→ `200 []` while the 12:43 from Umberleigh is in the feed. (The 2026-07-02 "found sound" note only
checked 0 against 1.) Fix: `val != 1`.

### 7.2 Fixed-link time windows are compared with service-day seconds, so links are refused after midnight — MEDIUM (live)
- [x] Fixed — the factory offers each link twice: for the service date with its window as published, and for the following calendar day with the window shifted by 86,400 s, so an arrival past 24:00 meets the next day's window and day flags. Test: `FixedLinkAfterMidnightTest`.

`RaptorAlgorithmFactory.java:295-302` passes `link.getStartTime()/getEndTime()` (wall-clock
seconds of day, e.g. `00:01:00`–`23:59:00`) into `Transfer`; `RaptorAlgorithm.scanTransfers` and
`scanTransfersRange` test `transfer.startTime() <= arrival && transfer.endTime() >= arrival` with
`arrival` in seconds since the *service* midnight, which runs past 86,400 for the feed's post-midnight
trains. An arrival at 24:01 (86,460) fails every window ending 23:59 (86,340). The companion
day-of-week check (`gtfs/.../FixedLink.java:223-243`, `runsOn`) reads the service date's weekday
rather than the wall-clock one, so a Sunday-only link is likewise not offered at 24:30 on a Saturday.

Live: `GET /?orig=LIV&dest=MCV&startDate=2026-09-08T22:55:00&endDate=2026-09-08T23:05:00` → `[]`,
although the 23:00 Liverpool train reaches Manchester Piccadilly at 00:01 and the 22-minute
MAN→MCV link (`00:01:00`–`23:59:00`) exists; `WAT→EUS 23:55-23:59` (23:57 to Vauxhall 00:01, Tube
link) → `[]`. In the feed 1,896 of 8,372 links end at 23:59:00. Fix: test the window against
`arrival mod 86400` (no link in the feed wraps midnight, but a `start > end` window should be handled
if one ever does) and evaluate `runsOn` for the wall-clock day of the arrival.

### 7.3 OBA `MISSING_VALUE` (−999) arrival/departure times are passed straight into the timetable — MEDIUM (latent)
- [ ] Fixed

`RaptorAlgorithmFactory.java:285-291` uses `st.getArrivalTime()`/`st.getDepartureTime()` without
`isArrivalTimeSet()`/`isDepartureTimeSet()`. A blank cell at a non-timepoint stop (spec-legal)
yields `StopTime(arrival=-999)`; the alight test `-999 + interchange < best` always passes, so the
stop is labelled with a negative arrival and marked, `RouteGrouping.overtakes` splits the route, and
`departureTimeIndex` records `-999` as a range departure. Neither shipped feed has blank cells, so
this is dormant until a feed change.

### 7.4 A trip with no stop_times aborts the whole build for every date its service runs — LOW-MEDIUM (latent)
- [ ] Fixed

`RaptorAlgorithmFactory.java:112-114` sorts with `t.departureTime(0)`, which is
`stopTimes().get(0)` on the empty list `dao.getStopTimesForTrip` returns for a trips.txt row absent
from stop_times.txt. `IndexOutOfBoundsException` from `createFromDao` instead of skipping the trip.

### 7.5 `ExtendedGtfsReader` swallows `EntityReferenceNotFoundException` for every entity type — LOW (latent)
- [ ] Fixed

`gtfs/src/main/java/com/joshuaharwood/velociraptor/gtfs/ExtendedGtfsReader.java:11-19`. The
override exists so links.txt rows naming unknown stops load with a warning (they are filtered in
`ExtendedGtfsRelationalDaoImpl.getAllFixedLinks`), but it also covers `stop_times.stop_id`,
`trips.route_id` etc. A mistyped stop id in stop_times loads with a WARN and a null stop; the
failure surfaces later as an NPE in `RaptorAlgorithmFactory.toTrip` for every date the trip runs,
or inside `CalendarServiceDataFactoryImpl.createService` for an unknown route. Stock OBA fails fast
at read time with a clear message.

## 8. Journey rendering and benchmarks (`rail`, `raptor-benchmarks`)

### 8.1 `RailJourneyFactory.apply` applies the range-only "fewer than 5 rounds" cut to depart-after queries — MEDIUM-LOW
- [x] Fixed — the cap is gone from both factories: every round the destination was improved in is returned, since a round-k arrival is only recorded when it beats every arrival with fewer legs. Tests: `LongJourneyTest`, `RailJourneyFactoryLongJourneyTest`.

`rail/src/main/java/com/joshuaharwood/velociraptor/rail/RailJourneyFactory.java:28-31, 50`.
`apply` delegates to `getResults(…, null)`, which skips `k >= 5` unconditionally. The base
`JourneyFactory.apply` has no cap on that path (the cap is documented as the range-mode rule), and
`RaptorAlgorithm.scan` runs until nothing improves, so round-5+ connections exist. `/first-arrival`
therefore returns `200 []` for a pair only reachable with five trains, and drops any round-5+
Pareto improvement, while `new DepartAfterQuery<>(raptor, new JourneyFactory())` returns them.
Fix: apply the cap only when `currentSearchTime != null`, or add it to `JourneyFactory.apply` so
the two factories agree.

### 8.2 `FactoryBuildBenchmark` measures a cache-warm build and hard-codes the service date — LOW
- [ ] Partly fixed (2026-09-12) — the date is now a `@Param serviceDateIso` (default 2026-06-03, as in `GtfsState`), so a swapped feed is benchmarked on the date it is given; the cache-warm measurement remains.

`raptor-benchmarks/src/main/java/com/joshuaharwood/velociraptor/raptor/benchmarks/FactoryBuildBenchmark.java:40-56`.
`DaoState.load()` only deserialises the feed; the first `createFromDao` then populates OBA's
`CalendarServiceImpl._data`, the relational trip/stop-time indexes and
`ExtendedGtfsRelationalDaoImpl.fixedLinksByServiceDate`, all of which land in the two warmup
iterations. The measured number is the steady-state build, not the startup cost the javadoc and
README describe. The date is fixed at `2025-09-17` rather than shared with `GtfsState`, so swapping
the feed as the README suggests silently benchmarks an empty timetable.

## 9. Server and deploy (`server`, Dockerfiles, CloudFormation)

### 9.1 Health checks probe port 8080, but the management interface moves `/q/health` to 9000 — HIGH (deploy-blocking, live)
- [ ] Fixed

`server/src/main/resources/application.properties:5` (`quarkus.management.enabled=true`);
`cloudformation/velociraptor-app.yaml:231, 270, 293` (container health command and both target
groups use `:8080/q/health/ready`); `Dockerfile.jvm:92` exposes 8080 only. Live:
`curl :8080/q/health/ready` → 404, `:9000/q/health/ready` → 200. Even with 5.1 fixed, ECS kills
the task after `StartPeriod` and no target ever becomes healthy. Fix: probe 9000 (and expose it) or
drop the management interface.

### 9.2 Packaging is `uber-jar` but `Dockerfile.jvm` copies the fast-jar layout — MEDIUM-HIGH
- [ ] Fixed

`application.properties:3` (`quarkus.package.jar.type=uber-jar`) vs `Dockerfile.jvm:87-90, 96`
(`COPY target/quarkus-app/{lib,*.jar,app,quarkus}`, `JAVA_APP_JAR=/deployments/quarkus-run.jar`).
`mvn package` produces `target/server-1.0-SNAPSHOT-runner.jar` and does not regenerate
`target/quarkus-app/`, so on a clean checkout the `COPY` fails; on a dirty one a stale
`quarkus-app/` is shipped. Independent of 5.2's build-context problem.

### 9.3 `/detail` and `/first-arrival` legs are serialised without the `type` discriminator — MEDIUM (live)
- [x] Fixed — `type()` carries `@JsonProperty("type")` on both leg records. Asserted in `RaptorResourceTest`.

`server/src/main/java/com/joshuaharwood/velociraptor/server/http/dto/RailJourneyLeg.java:9-16, 27-28, 38-39`.
`@JsonTypeInfo(include = EXISTING_PROPERTY, property = "type")` relies on a `type` bean property,
but `type()` is not a record component and has no `get` prefix, so Jackson never writes it. Live
`/detail` output for a fixed-link leg is `{"origin","destination","departureTime":null,"arrivalTime":null,"durationSeconds",…}`
with no `type`; clients can only sniff field presence. Fix: `@JsonProperty("type")` on `type()`,
or `include = PROPERTY`.

### 9.4 Every custom OpenTelemetry metric is a no-op — MEDIUM
- [ ] Fixed

`RaptorController.java:85-99, 225, 258-260`, `GtfsDaoProducer.java:34-37, 47`.
`quarkus.otel.metrics.enabled` is a build-time property defaulting to `false`
(`MetricsBuildConfig`, quarkus-opentelemetry 3.36.3) and is set nowhere, so the injected `Meter`
is the no-op one: `raptor.journeys.returned`, `raptor.servicedate.cache.miss`,
`raptor.servicedate.cache.size` and `gtfs.load.seconds` are never recorded. The CloudFormation
`QUARKUS_OTEL_METRICS_EXPORTER=otlp` env var cannot help because the exporter is build-time too.
Fix: `quarkus.otel.metrics.enabled=true` in `application.properties`.

### 9.5 `@Timeout(5s)` cannot stop a scan; the client then gets a 503 "at capacity" for a timeout — MEDIUM-LOW
- [ ] Fixed

`RaptorResource.java:28-30`, `FaultToleranceExceptionMapper.java:16-19`. SmallRye's synchronous
`Timeout` only interrupts the thread; the RAPTOR loops never check interruption, so the request
computes the full result, discards it, and throws `TimeoutException` - which the mapper renders as
503 "Server at capacity" with `Retry-After: 10`. A 40 s query holds a `@Bulkhead` slot for 40 s and
the client is told to retry. Fix: check interruption in the scan loops or drop `@Timeout`; map
`TimeoutException` separately.

### 9.6 Malformed or offset-bearing `startDate`/`endDate` return 404 — LOW-MEDIUM (live)
- [x] Fixed — the parameters are `OffsetDateTime`s read by `WindowDateTimeParamConverterProvider`: offset-bearing input is the contract, zone-less input is still read as Europe/London (deprecated, WARN), and anything else is a 400 problem naming the parameter. Tests: `WindowDateTimeConverterTest`, `RaptorResourceTest`.

`RaptorResource.java:43-44, 59-60, 75`. Quarkus REST parses `LocalDateTime` with
`ISO_LOCAL_DATE_TIME` only and wraps a query-parameter conversion failure in `NotFoundException`.
Live: `startDate=2026-09-08T07:45:00Z` → 404 with an empty body, as does the API's own output
format `…T09:01:00+01:00`. Indistinguishable from a wrong URL; missing params correctly get 400.
Fix: take a `String` and parse (400 on failure), or register a `ParamConverter`.

### 9.7 Inverted or empty windows are accepted — LOW
- [ ] Fixed

`RaptorResource.java:48-51, 64-67`. Nothing checks `endDate > startDate`; `RangeQuery.plan`
filters `t >= start && t < end`, so `end <= start` yields a silent `200 []`, identical to "no
service".

### 9.8 Stop codes are neither normalised nor validated — LOW
- [ ] Fixed

`RaptorResource.java:41-42, 45, 94-98`; `RaptorController.java:224, 233, 241, 245-247`.
`orig=sca` or a typo → `200 []`. `orig=SCA&notVia=sca` passes `validateNotVia` (case-sensitive)
and the not-via is then silently dropped because `Stop("sca")` is unindexed. Fix: upper-case/trim
and reject codes absent from the DAO with 400.

### 9.9 `validateNotVia` 400 body is a bare string sent as `application/json` — LOW
- [ ] Fixed

`RaptorResource.java:26, 97`. The class-level `@Produces` supplies the content type for a plain
string entity; the validator's own 400s on the same endpoints are JSON objects.

### 9.10 Stale comment — INFO
- [ ] Fixed

`S3GtfsLoader.java:29` says the loader is active under the `prod` profile; selection is actually by
`s3://` prefix in `GtfsDaoProducer.java:42` and there is no `%prod` scoping anywhere.

## Investigated and not a bug: Saunderton → Marylebone "07:45 outside the window"

Report: a search for 09:00 (`…/SDR/MYB/2026-09-08T07:45:00Z/2026-09-08T08:30:00Z`) returned the
07:45 and 09:01 trains; a search for 08:45 (`…T07:30:00Z/…T08:15:00Z`) returned 07:45, 08:30 and
09:01. On 2026-09-08 the trains that call at Saunderton for Marylebone depart 06:44, 07:45, 08:30,
09:01 and 09:52 (everything else passes through). Live results:

| window passed to `/` (local) | returned |
|---|---|
| 08:45–09:30 (07:45Z–08:30Z converted to BST) | 09:01 |
| 08:30–09:15 (07:30Z–08:15Z converted) | 08:30, 09:01 |
| 07:45–08:30 (UTC digits passed as local) | 07:45 |
| 07:30–08:15 (UTC digits passed as local) | 07:45 |

Each reported result set is exactly the union of the correctly converted call and the
UTC-digits-as-local call. The engine and the HTTP layer return the right trains for the window they
are given (`/first-arrival` and `/detail` agree). The literal `Z` form is rejected with 404 (9.6), so
the caller is stripping the suffix and, for at least one of its calls, not converting to Europe/London
before hitting `/`. The fix belongs in the caller; 9.6 would make the mistake visible.
