# velociRAPTOR

A Java implementation of the [RAPTOR](https://www.microsoft.com/en-us/research/publication/round-based-public-transit-routing/)
transit-routing algorithm. It plans optimal public-transport journeys over GTFS timetables and
serves them from an HTTP API.

The engine implements RAPTOR proper (round-based earliest arrival) and rRAPTOR (range queries with
label retention), with first-class support for *fixed links* — connections that are not timetabled
services, such as a Tube ride, a ferry crossing or a walk between neighbouring stations.

## What it aims for

**Correctness first.** The scan is checked against a brute-force oracle: `ScanOracleTest` compares
it with a per-round relaxation over individual trips across thousands of random timetables,
including dwells shorter than the interchange and stops reachable only by a fixed link. Every
divergence from the reference is pinned by a regression test; the sample feed is locked by a golden
test. Over 200 tests across five modules.

**Then performance, measured.** Range queries use rRAPTOR label retention rather than a scan per
departure time, and multi-day searches share one copy of the timetable through an offset overlay —
roughly 4× faster than the rollover it replaced. `raptor-benchmarks` tracks it with JMH.

## Derived from planarnetwork/raptor

**velociRAPTOR is a derivative work of [planarnetwork/raptor](https://github.com/planarnetwork/raptor)
by [Linus Norton](https://github.com/linusnorton)**, a TypeScript implementation of the same
algorithm, published under the GNU General Public License v3.0.

This project began as a port of that codebase. The structure of the algorithm, the query types, the
journey model and much of the naming follow it directly; the Java code is a translation of that work
before it is anything of its own. Where behaviour has since diverged, it is because a specific
defect or design difference was identified and pinned by a test — those are catalogued below and in
[`BUGS.md`](BUGS.md). Credit for the original design and implementation belongs to
[Linus Norton](https://github.com/linusnorton).

Because the original is GPL-3.0, this port is GPL-3.0 too.

### Upstream and related work

| Project | |
|---|---|
| [linusnorton](https://github.com/linusnorton) | The original author |
| [planarnetwork](https://github.com/planarnetwork) | The organisation publishing the projects below |
| [planarnetwork/raptor](https://github.com/planarnetwork/raptor) | Implementation of the Route Based Public Transit Algorithm (RAPTOR) — this project's direct ancestor |
| [planarnetwork/gb-transit](https://github.com/planarnetwork/gb-transit) | DTD feed importing, timetable conversion and GTFS tooling |

## Licence

GNU General Public License v3.0 — the full text is in [`LICENSE`](LICENSE).

This licence is inherited, not chosen: velociRAPTOR is a derivative work of a GPL-3.0 codebase, so
it must be distributed under the same terms. You may use, study, modify and redistribute this
software, but derivative works must also be released under the GPL-3.0.

## Modules

| Module | What it does |
|---|---|
| `raptor` | The algorithm itself: the round-based scan, depart-after and range queries, journey construction. No GTFS knowledge. |
| `gtfs` | GTFS reading on top of the OneBusAway GTFS libraries, extended with this project's `links.txt` fixed-link format. |
| `raptor-oba-bridge` | Builds the algorithm's timetable structures from a loaded GTFS feed, per service date. |
| `rail` | Rail-oriented journey rendering: full stop lists, train UIDs, fixed-link leg times. |
| `server` | Quarkus REST API over the engine. See [`server/README.md`](server/README.md). |
| `server-load-testing` | Gatling load tests treating the server as a black box. See [`server-load-testing/README.md`](server-load-testing/README.md). |
| `raptor-benchmarks` | JMH microbenchmarks, built standalone outside the reactor. See [`raptor-benchmarks/README.md`](raptor-benchmarks/README.md). |

## Build

Java 25 is required. The Maven wrapper pins Maven 3.9.11 and downloads it on first use, so no local
Maven installation is needed:

```
./mvnw verify
```

## Run

The server loads the artificial sample feed by default, so it starts with no external dependencies:

```
./mvnw -pl server -am package -DskipTests
cd server && java -Dvelociraptor.raptor.servicedate.precompute=false \
  -jar target/quarkus-app/quarkus-run.jar
```

Then:

```
curl 'http://localhost:8080/detail?orig=BTN&dest=MKC&startDate=2026-06-03T12:30:00%2B01:00&endDate=2026-06-03T13:05:00%2B01:00'
```

## HTTP API

All endpoints are `GET` and return JSON arrays of journeys. Full parameter semantics are in
[`server/README.md`](server/README.md); `server/openapi/openapi.yaml` is the generated OpenAPI 3.1
description.

| Path | Returns |
|---|---|
| `/` | Range query over a departure window, slim legs |
| `/detail` | Range query, full stop lists and train UIDs |
| `/first-arrival` | One journey per number of trains, earliest arrival departing at or after `startDate` |

`startDate` and `endDate` are instants — ISO 8601 with a UTC offset (`2026-06-03T12:30:00+01:00`),
the same form the responses use. A zone-less value is accepted and read as Europe/London
wall-clock, but is deprecated and logged at WARN. Anything else is a 400. Every error is an
RFC 9457 `application/problem+json` body.

## Configuration

Server settings are MicroProfile Config properties, so each can be set in `application.properties`
or as an environment variable (upper-case, dots to underscores). The routing ones are assembled
once into `RaptorAlgorithmConfig` and logged at startup.

| Property | Default | Meaning |
|---|---|---|
| `velociraptor.gtfs.source.path` | `../fixtures/gtfs-sample` | GTFS to load: a local zip or directory, or an `s3://bucket/key` URI |
| `velociraptor.raptor.servicedate.precompute` | `true` | Build the algorithm for every service date at startup; readiness waits for it |
| `velociraptor.raptor.fixedlinks.forbidleading` | `true` | A journey may not begin with a fixed link |
| `velociraptor.raptor.fixedlinks.forbidtrailing` | `true` | A journey may not end with a fixed link |
| `velociraptor.raptor.fixedlinks.forbidcontiguous` | `true` | A journey may not take two fixed links in a row |

## Fixed links

A fixed link is any `links.txt` connection that is not a timetabled train: a walk, a Tube ride, a
ferry. A link carries a mode, a transit time, a time window and a day pattern.

The three rules above are enforced inside the scan (`FixedLinkRules`), not by filtering results, so
when a rule removes a link the train journey it would have out-competed is returned instead. A link
sums the minimum interchange at both of its ends: arriving off a service charges an entry
interchange, and the onward departure charges an exit interchange.

With one label per stop and round, `forbidcontiguous` has one known gap: a stop whose label in a
round came from a fixed link cannot be left by a fixed link even if a train also reached it in that
round, a little later than the link. Library callers (`DepartAfterQuery`, `RangeQuery`) default to
no rules.

## Sample feed

The repository carries no real timetable. `fixtures/gtfs-sample` is an artificial GTFS feed over a
real corner of the network (Brighton Main Line, Thameslink, both Coastways, the Marshlink, the
Portsmouth to Ryde ferry and a WCML stub) with an invented four-week timetable in June 2026.
Stations, CRS codes, operators and geography are real so that queries read like the ones the
service answers; every time, calendar and fixed link is invented, so nothing is derived from a
licensed extract.

It is the default `velociraptor.gtfs.source.path`, the fixture for the bridge and server tests, and
the default dataset for the benchmarks and load tests. It is generated from `SampleFeed` in the
`gtfs` module's tests and pinned by `SampleFeedGoldenTest`;
[`fixtures/gtfs-sample/README.md`](fixtures/gtfs-sample/README.md) describes the network and the
journeys it is built to exercise.

Real extracts for local runs go in `data/`, which is not tracked.

## Divergences from the reference implementation

Each of these is a deliberate difference from planarnetwork/raptor, identified during or after the
port and locked by a test. Several were reported upstream. [`BUGS.md`](BUGS.md) holds the full
analysis, including findings that were refuted on inspection.

| # | Divergence | Tests |
|---|---|---|
| 1 | Journey reconstruction could dereference a missing connection on range queries; the reference throws on the same inputs | `RangeQueryDanglingPointerTest` |
| 2 | Leg and stop times serialise as `OffsetDateTime` resolved at Europe/London, not a zone-less `LocalDateTime` | `RaptorResourceTest` |
| 3 | BST/GMT conversion was an hour wrong on the two transition days | `ServiceDateConverterTest` |
| 4 | The route scan's trip cursor goes stale and drops catchable trips (upstream #52) | `RouteScannerResetTest` |
| 5 | Overtaking detection was incomplete, so trips sharing a route were mis-grouped (upstream #51) | `RouteGroupingTest` |
| 6 | The re-board rule missed a trip still dwelling at the stop, and could discard the held trip | `RaptorAlgorithmReboardTest`, `ScanOracleTest` |
| 7 | Stops reached only by a fixed link were unreachable; an unknown origin crashed the scan (upstream #38, #42) | `FootpathDestinationTest`, `RailJourneyFactoryFixedLinkOnlyTest` |
| 8 | Journeys of five or more legs were dropped by a product filter carried over from the reference | `LongJourneyTest`, `RailJourneyFactoryLongJourneyTest` |
| 9 | Range queries returned journeys dominated by a later departure when a fixed link ended them | `RangeDominationTest` |
| 10 | A not-via stop ended the route scan, losing every boarding beyond it | `NotViaTest` |
| 11 | Range queries never started a journey with a fixed link (the paper's Ψ departure set) | `RangeSeedingTest` |
| 12 | Request stops (`pickup_type`/`drop_off_type` = 3) were treated as unboardable | `RequestStopTest` |
| 13 | Fixed links were refused after midnight, when a service-date time runs past 24:00 | `FixedLinkAfterMidnightTest` |
| 14 | Fixed-link legs had no times, and a leading link was dated at whichever range seed found it | `RailJourneyFactoryFixedLinkTimesTest` |

`ScanOracleTest` backs several of these with a property check: the scan is compared against a
brute-force per-round relaxation over individual trips on thousands of random timetables, with no
exclusions and with dwells shorter than the interchange.
