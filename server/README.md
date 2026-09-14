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
  day (> 86400s) correctly includes GTFS after-midnight departures past 24:00.

  A zone-less value (`2026-06-03T12:30:00`) is still accepted and read as Europe/London wall-clock,
  but is deprecated and logged at WARN. Anything else is a 400 `application/problem+json` whose
  `detail` names the parameter.

A search covers a single service day. Fixed-link use is governed by the
`velociraptor.raptor.fixedlinks.*` settings documented in the [root README](../README.md): by
default a journey may not begin or end with a fixed link, or use two in a row.

### Example

```
curl 'http://localhost:8080/detail?orig=BTN&dest=MKC&startDate=2026-06-03T12:30:00%2B01:00&endDate=2026-06-03T13:05:00%2B01:00'
```

## Errors

Every error the API returns is an RFC 9457 `application/problem+json` body, produced by
`quarkus-http-problem`. Its `OASFilter` fills in the problem content for any error response an
operation declares, which is why the `@APIResponse` annotations carry a description but no schema.

That includes the 503 from `FaultToleranceExceptionMapper`: a saturated bulkhead or a timed-out
query is the same shape as everything else, with `Retry-After` still set.

## OpenAPI

`openapi/openapi.yaml` (and `.json`) is the OpenAPI 3.1 description, generated from the JAX-RS
annotations by `quarkus-smallrye-openapi` and rewritten on every build — treat it as output. To
change it, change the annotations on `RaptorResource`, not the file. It is committed so clients can
generate against it without building the server.

The live document is served at `/q/openapi`, with Swagger UI at `/q/swagger-ui` in dev mode.

## Running locally

The prod profile loads GTFS from a local path (`velociraptor.gtfs.source.path`, default
`../fixtures/gtfs-sample`, relative to this module), so no S3 or Docker is needed:

```
./mvnw -pl server -am package -DskipTests
cd server && java -Dvelociraptor.raptor.servicedate.precompute=false \
  -jar target/quarkus-app/quarkus-run.jar
```

`velociraptor.raptor.servicedate.precompute=true` precomputes a `RaptorAlgorithm` per service date
at startup, and the readiness probe waits for it. `false` starts immediately and builds each date on
first use.

### Dev mode

```
./mvnw -pl server -am quarkus:dev
```

The Quarkus Dev UI is available in dev mode at <http://localhost:8080/q/dev/>.

### Tests

Only the `@QuarkusTest` suite needs Docker, for the LocalStack S3 fixture.

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
