# server

Quarkus REST API exposing the RAPTOR routing engine.

## Endpoints

All endpoints are `GET` and return JSON arrays of journeys.

| Path | Returns | Required params | Optional params |
|---|---|---|---|
| `/` | `SimpleJourney` (slim legs) | `orig`, `dest`, `startDate`, `endDate` | `notVia` (repeatable) |
| `/detail` | `RailJourney` (full stop lists, train UIDs) | `orig`, `dest`, `startDate`, `endDate` | `notVia` |
| `/first-arrival` | `RailJourney` — one journey per number of trains, earliest arrival departing at or after `startDate` | `orig`, `dest`, `startDate` | `notVia` |

### Parameters

- **`orig` / `dest` / `notVia`** — stop ids (CRS codes like `BTN`). `notVia` excludes journeys that
  call at or ride through the given stop(s). A 400 is returned if `orig` or `dest` also appear in
  `notVia`. An unknown stop returns an empty list.
- **`startDate` / `endDate`** — instants in ISO 8601 with a UTC offset
  (`2026-06-03T12:30:00+01:00`, `2026-06-03T11:30:00Z`), the same form the responses use. They are
  moved onto the Europe/London wall-clock before use: the service date is `startDate`'s London date,
  and the window is expressed in seconds from that date's midnight, so an `endDate` on the following
  day (> 86400s) correctly includes GTFS after-midnight departures past 24:00. `endDate` must be after
  `startDate`, compared as instants; one at or before it is a 400 with an RFC 9457 `application/problem+json` body whose `detail` says so.

  A zone-less value (`2026-06-03T12:30:00`) is still accepted and read as Europe/London wall-clock,
  but is deprecated and logged at WARN. Anything else is a 400 `application/problem+json` whose
  `detail` names the parameter.

A search covers a single service day. Fixed-link use is governed by the
`velociraptor.raptor.fixedlinks.*` settings documented in the [root README](../README.md): by
default none of them applies: a journey may begin or end with a fixed link, or use two in a row.

### Legs

A leg of either kind of journey is a rail leg or a fixed link, told apart by its `type`: `RAIL_LEG` or
`FIXED_LEG`. Each type carries only the fields that apply to it:

| | rail leg | fixed link |
|---|---|---|
| `origin`, `destination`, `departureTime`, `arrivalTime`, `duration` | yes | yes |
| `boardingInterchange` | yes, `null` on the first leg | yes, `null` on the first leg |
| `originTrainUid`, `destinationTrainUid`, `operator` | yes, `null` where the trip has none | not present |
| `originPickUpType`, `destinationDropOffType` | yes | not present |
| `mode` | not present | yes, `null` where the feed gives none |

A field a type does not have is left out, never sent as `null`; a field it has is always present,
`null` where there is no value: a trip with no train UID, such as a TfL trip, is still a rail leg,
with null UIDs. `/detail` rail legs also carry the train's `trainTrip` and the `startIndex` and `endIndex`
of the leg within it. The OpenAPI document marks every field of every response type required, and the
ones that may be null nullable; `OpenApiResponseSchemaTest` keeps it that way.

### Example

```
curl 'http://localhost:8080/detail?orig=BTN&dest=MKC&startDate=2026-06-03T12:30:00%2B01:00&endDate=2026-06-03T13:05:00%2B01:00'
```

## Errors

Every error the API returns is an RFC 9457 `application/problem+json` body, produced by
`quarkus-http-problem`. Its `OASFilter` fills in the problem content for any error response an
operation declares, which is why the `@APIResponse` annotations carry a description but no schema.

That includes the 503 from `FaultToleranceExceptionMapper`: a saturated bulkhead or a timed-out
query is the same shape as everything else, with `Retry-After` still set, and its `detail` says
which of the two it was.

The errors the API raises itself are built with `HttpProblem.builder()`, with a `detail` that says why the
request was rejected, in terms of the request:

| Status | When | `detail`, for example |
|---|---|---|
| 400 | `endDate` is not after `startDate` | `endDate=... must be after startDate=...: the window holds no departures.` |
| 400 | `orig` or `dest` is in `notVia` | `notVia=[BTN] includes the origin, orig=BTN: a journey cannot avoid the stop it starts at.` |
| 400 | a window parameter is not ISO 8601 | `startDate=nonsense is not an ISO 8601 date-time; expected an offset form such as ...` |
| 503 | at capacity, or the time limit was exceeded | which of the two, and to retry after `Retry-After` |

The errors the framework raises - a missing or blank parameter (with its `violations`), an unknown
path, another method, an `Accept` header without JSON, an unexpected error - are problems in
`quarkus-http-problem`'s own wording.

## OpenAPI

`openapi/openapi.yaml` (and `.json`) is the OpenAPI 3.1 description, generated from the JAX-RS
annotations by `quarkus-smallrye-openapi` and rewritten on every build — treat it as output. To
change it, change the annotations on `RaptorResource`, not the file. It is committed so clients can
generate against it without building the server.

The live document is served at `/q/openapi`, with Swagger UI at `/q/swagger-ui` in dev mode.

## Running locally

The feed has no default: `velociraptor.gtfs.source.path` (a local zip or directory, or an `s3://`
URI) and `velociraptor.gtfs.source.format` (`gb-transit`, or the deprecated `dtd2gtfs`) are both
required, and the server does not start without them. A local path needs no S3 or Docker:

```
./mvnw -pl server -am package -DskipTests
cd server && java -Dvelociraptor.gtfs.source.path=../fixtures/gtfs-sample-gb-transit \
  -Dvelociraptor.gtfs.source.format=gb-transit \
  -Dvelociraptor.raptor.servicedate.precompute=false \
  -jar target/quarkus-app/quarkus-run.jar
```

A gb-transit release works the same way, e.g.
`-Dvelociraptor.gtfs.source.path=/data/gtfs-national-rail-only.zip`.

`velociraptor.raptor.servicedate.precompute=true` precomputes a `RaptorAlgorithm` per service date
at startup, and the readiness probe waits for it. `false` starts immediately and builds each date on
first use.

### Dev mode

```
./mvnw -pl server -am quarkus:dev \
  -Dvelociraptor.gtfs.source.path=../fixtures/gtfs-sample-gb-transit -Dvelociraptor.gtfs.source.format=gb-transit
```

The Quarkus Dev UI is available in dev mode at <http://localhost:8080/q/dev/>.

### Tests

Only the `@QuarkusTest` suite needs Docker, for the LocalStack S3 fixture. It serves
`fixtures/gtfs-sample-gb-transit`; `-Dvelociraptor.test.gtfs` and `-Dvelociraptor.test.gtfs.format`
point it at another feed.

## Packaging

`./mvnw package` produces `target/quarkus-app/quarkus-run.jar`. This is not an über-jar — the
dependencies are copied into `target/quarkus-app/lib/`.

For a single runnable jar:

```
./mvnw -pl server -am package -Dquarkus.package.jar.type=uber-jar
java -jar server/target/*-runner.jar
```

For a native executable (requires GraalVM, or a container build):

```
./mvnw -pl server -am package -Dnative
./mvnw -pl server -am package -Dnative -Dquarkus.native.container-build=true
```

See the [Quarkus Maven tooling guide](https://quarkus.io/guides/maven-tooling) for the details.

## Management interface

The management interface runs on port 9000: `/q/health` for liveness and readiness. Readiness gates
on precompute via `PrecomputeReadinessCheck`, so a container is not routed traffic until every
service date is built.
