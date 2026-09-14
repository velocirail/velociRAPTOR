# server-load-testing

[Gatling](https://docs.gatling.io/) load tests for the velociRAPTOR HTTP API, currently targeting
`GET /` (the range query endpoint). They answer two questions per deployment shape: **what request
rate can it sustain** (capacity), and **does it still meet the SLA at the rate we run it at**
(steady state).

The simulations treat the server as a black box over HTTP. This module deliberately does not depend
on the `server` module, so Quarkus's Netty never meets Gatling's on one classpath.

## A note on the dataset

These simulations run against whatever feed the server was started with. The repository ships only
`fixtures/gtfs-sample`, an artificial feed of a few hundred trips — large enough to exercise every
code path, far too small to say anything about production capacity. Treat runs against the sample
feed as a check that the harness works, not as performance figures.

To measure capacity meaningfully, point the server at a full-size feed of your own with
`-Dvelociraptor.gtfs.source.path=...` and size the heap for it. Startup cost and memory scale with
the number of service dates in the feed when precompute is enabled.

## Running

1. **Build and start the server.** Production runs with precompute enabled, so test that way by
   default — it builds every service date in the feed at startup and the readiness probe gates on
   it:

   ```bash
   ./mvnw -B -pl server -am package -DskipTests
   cd server && java \
     -Dvelociraptor.gtfs.source.path=../fixtures/gtfs-sample \
     -Dvelociraptor.raptor.servicedate.precompute=true \
     -jar target/quarkus-app/quarkus-run.jar
   ```

   Wait for readiness — `curl localhost:9000/q/health/ready` gates on precompute. For quick
   iteration on the harness itself, `precompute=false` starts immediately and relies on the
   simulations' warm-up request instead, but its numbers flatter the server relative to the
   production configuration.

2. **Smoke test** (seconds — proves server, fixture and date line up):

   ```bash
   ./mvnw -pl server-load-testing gatling:test \
     -Dgatling.simulationClass=com.joshuaharwood.velociraptor.loadtesting.SmokeSimulation
   ```

3. **Capacity test** (~6 min with defaults — finds the load limit):

   ```bash
   ./mvnw -pl server-load-testing gatling:test \
     -Dgatling.simulationClass=com.joshuaharwood.velociraptor.loadtesting.CapacitySimulation
   ```

4. **Steady-state SLA check** at the rate the capacity run said is sustainable:

   ```bash
   ./mvnw -pl server-load-testing gatling:test \
     -Dgatling.simulationClass=com.joshuaharwood.velociraptor.loadtesting.SteadyStateSimulation \
     -Dloadtest.rps=10
   ```

Without `-Dgatling.simulationClass` the plugin prompts, or runs everything with
`-Dgatling.runMultipleSimulations=true`. Always pass the class in CI.

## Simulations

| Simulation | Injection model | Question it answers |
|---|---|---|
| `SmokeSimulation` | 5 users at once | Is the setup wired correctly? Any failure fails the run. |
| `CapacitySimulation` | Open, stepped ramp: `startRps` + `steps`×`stepRps`, each level held `levelSeconds` | Where is the knee? Open injection keeps arrivals coming when the server slows, so saturation shows up as responses/sec flat-lining below requests/sec and percentiles turning vertical. |
| `SteadyStateSimulation` | Open, constant `rps` for `durationSeconds` | Does the SLA (p95 < `p95Millis`, failures < `maxFailedPercent`%) hold at the rate we intend to run? Use as a CI regression gate. |

All requests draw from a feeder of 24 origin/destination pairs across major fixture stations
(commuter, cross-London fixed-link and long-distance flows) with a random two-hour window on a
random quarter-hour between 06:00 and 20:00, so work is spread across the timetable and nothing can
be served from a response cache.

## Tuning knobs (`-Dloadtest.*`)

| Property | Default | Used by |
|---|---|---|
| `loadtest.baseUrl` | `http://localhost:8080` | all |
| `loadtest.serviceDate` | `2026-06-03` | all (must be a valid service date in the loaded GTFS) |
| `loadtest.windowMinutes` | `120` | all (range query window length) |
| `loadtest.startRps` / `stepRps` / `steps` / `levelSeconds` | `5` / `5` / `10` / `30` | `CapacitySimulation` |
| `loadtest.rps` / `durationSeconds` | `10` / `120` | `SteadyStateSimulation` |
| `loadtest.p95Millis` / `maxFailedPercent` | `1000` / `1.0` | `SteadyStateSimulation` |

## Cache warm-up

The first request for a service date builds the whole `RaptorAlgorithm` for that date and caches it
(`RaptorController.algorithmCache`) — potentially tens of seconds on a full-size feed when the
server runs with `precompute=false`. Every simulation therefore fires one blocking warm-up request
in `before()` and fails fast if the server is down, so the numbers measure steady-state query cost
rather than cache builds.

With `precompute=true` (the production configuration) the cache is already fully built behind the
readiness gate and the warm-up request is a cheap no-op, so the same simulations run unchanged
against either configuration.

## Metrics you get

**From Gatling** — `target/gatling/<run>/index.html` after each run:

- requests/sec and responses/sec over time (their divergence is the saturation signal)
- response-time percentiles over time (p50/p75/p95/p99, min/max) and distribution
- active users over time (in an open model, growth here means requests are backing up)
- failures by error type over time (timeouts, connection errors, HTTP status)
- per-request stats table with mean/stddev and the standard percentile bands

**From the server**, complementing Gatling from the inside:

- OpenTelemetry metrics in `RaptorController`: journeys returned per range query (histogram),
  algorithm-cache misses (counter — nonzero during a run means a query slipped to an uncached
  date), cache size (gauge). Exported via the Quarkus OTel extension when an OTLP endpoint is
  configured.
- Quarkus management interface on port 9000: `/q/health` liveness and readiness, the latter gating
  on precompute via `PrecomputeReadinessCheck`.
- JVM-level: run the server with `-Xlog:gc*:file=gc.log` and/or a profiler during a capacity run to
  see whether the ceiling is CPU (expected — each query is a full RAPTOR range scan) or GC.
