# raptor-benchmarks

JMH microbenchmarks for the `raptor` module. This module sits outside the reactor's test lifecycle —
packaging produces an executable uber-jar but never runs it, so benchmarks only run when you ask for
them.

## Build

```
./mvnw -pl raptor-benchmarks -am package -DskipTests
```

Output: `raptor-benchmarks/target/benchmarks.jar`.

## Run

Always run from the repository root so the default GTFS fixture path resolves.

```
# All benchmarks (long — the retention and multi-day ones are parametrised):
java -jar raptor-benchmarks/target/benchmarks.jar

# One by name (regex match on class):
java -jar raptor-benchmarks/target/benchmarks.jar ScanBenchmark
java -jar raptor-benchmarks/target/benchmarks.jar DepartAfterQueryBenchmark
java -jar raptor-benchmarks/target/benchmarks.jar RangeQueryBenchmark
java -jar raptor-benchmarks/target/benchmarks.jar FactoryBuildBenchmark
java -jar raptor-benchmarks/target/benchmarks.jar RRaptorRetentionBenchmark
java -jar raptor-benchmarks/target/benchmarks.jar MultiDayBenchmark

# Smoke (1 warmup iteration, 1 measurement iteration, 1 fork, 2s each):
java -jar raptor-benchmarks/target/benchmarks.jar ScanBenchmark -wi 1 -i 1 -f 1 -r 2 -w 2

# Pin parameters to one case:
java -jar raptor-benchmarks/target/benchmarks.jar MultiDayBenchmark -p route=VIC\>BTN -p numDays=2 -p windowHours=3
```

### Useful flags

```
# Save results as JSON for before/after comparisons:
java -jar raptor-benchmarks/target/benchmarks.jar -rf json -rff baseline.json

# CPU flamegraph via async-profiler (requires libasyncProfiler on the system):
java -jar raptor-benchmarks/target/benchmarks.jar ScanBenchmark -prof async:output=flamegraph

# Allocation profile:
java -jar raptor-benchmarks/target/benchmarks.jar ScanBenchmark -prof gc

# Swap the GTFS dataset:
java -Draptor.benchmark.gtfs=/path/to/other.zip -jar raptor-benchmarks/target/benchmarks.jar
```

## Benchmarks

| Class | What it measures | Mode |
|---|---|---|
| `ScanBenchmark.scanFromOrigin` | Hot path — `raptor.scan()` only, single origin | avg time |
| `DepartAfterQueryBenchmark.plan` | Full query path plus `JourneyFactory` construction | avg time |
| `RangeQueryBenchmark.planOneHourWindow` | One-hour departure window (`RangeQuery`, label-retention rRAPTOR) | avg time |
| `FactoryBuildBenchmark.createFromDao` | Startup and preload cost | single-shot |
| `RRaptorRetentionBenchmark` | Single-day range: naive per-departure scans vs label-retention rRAPTOR, parametrised by route and window length | avg time |
| `MultiDayBenchmark` | Multi-day range: per-day rollover vs offset overlay (`createMultiDayOverlayFromDao`), plus overlay vs materialised-flatten build cost; parametrised by route, horizon and window | avg time |

All use a `GtfsState` fixture loaded once per trial. Origin `BTN` (Brighton), destination `VIC`
(London Victoria), departing 13:00 local on service date 2026-06-03 (override with
`-p serviceDateIso=`).

## On the default dataset

The default fixture is the artificial sample feed (`fixtures/gtfs-sample`, a few hundred trips),
which is far too small to say anything about performance — it exists so the benchmarks are runnable
out of the box, not so their numbers mean something.

For figures worth quoting, supply a full-size feed of your own:

```
java -Draptor.benchmark.gtfs=/path/to/real.zip -p serviceDateIso=YYYY-MM-DD \
  -jar raptor-benchmarks/target/benchmarks.jar
```

## Indicative figures

These were measured on a full-size production rail feed (~4,200 services, ~131k trips) on JDK 25.
That dataset is not in the repository, so these are not reproducible here and are recorded only for
the shape of the relationships between them.

```
Benchmark                              Mode  Cnt    Score    Error  Units
ScanBenchmark.scanFromOrigin           avgt    5   27.381 ±  4.631  ms/op
DepartAfterQueryBenchmark.plan         avgt    5   25.188 ±  2.901  ms/op
RangeQueryBenchmark.planOneHourWindow  avgt    3  129.206 ± 74.906  ms/op
FactoryBuildBenchmark.createFromDao      ss   10  119.428 ± 18.888  ms/op
```

- Scan ≈ DepartAfter within error — journey construction is nearly free on top of the scan.
- The range query figure above predates the rRAPTOR label-retention work and reflects the naive
  "re-run the scan per start time" cost. `RangeQueryBenchmark` now measures the retention path; use
  `RRaptorRetentionBenchmark` for an explicit before/after comparison.
- Error bars widen sharply at low iteration counts. Bump `-i 5` or `-i 10` when tracking small
  changes.

Multi-day, same caveats — 2-day horizon, 3h window, 2 warmup and 3 measurement iterations, so the
error bars are wide:

```
MultiDayBenchmark.buildFlatten     avgt 3   773 ± 144  ms/op   (materialised copy of each day's stop times)
MultiDayBenchmark.buildOverlay     avgt 3   753 ± 610  ms/op   (OffsetTrip wrappers, shared stop times)
MultiDayBenchmark.overlayRange     avgt 3   809 ± 200  ms/op   (one label-retention query over the overlay)
MultiDayBenchmark.rolloverRange    avgt 3  3242 ± 1194 ms/op   (independent per-departure rollover scans)
```

Overlay build ≈ flatten build while sharing the timetable, and the overlay range query is roughly
4× faster than the rollover path it replaced.

## CI

CI compiles and packages this module but never invokes `java -jar benchmarks.jar`. Benchmarks run
only when someone triggers them locally or in a dedicated performance pipeline.
