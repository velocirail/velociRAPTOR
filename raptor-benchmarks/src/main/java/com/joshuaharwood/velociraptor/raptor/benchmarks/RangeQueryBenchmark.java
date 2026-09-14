package com.joshuaharwood.velociraptor.raptor.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * Profile query over a one-hour departure window, single-day rRAPTOR path (label retention).
 * For the before/after comparison against the naive per-departure baseline, see
 * {@link RRaptorRetentionBenchmark}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(1)
public class RangeQueryBenchmark {

  private RangeQuery<Journey> query;
  private LocalDate queryDate;
  private int windowStart;
  private int windowEnd;

  @Setup
  public void setup(GtfsState state) {
    this.query = new RangeQuery<>(state.raptor, new JourneyFactory());
    var sd = state.parseServiceDate();
    this.queryDate = LocalDate.of(sd.getYear(), sd.getMonth(), sd.getDay());
    this.windowStart = state.departTime;
    this.windowEnd = state.departTime + (int) TimeUnit.HOURS.toSeconds(1);
  }

  @Benchmark
  public void planOneHourWindow(GtfsState state, Blackhole bh) {
    var journeys = query.plan(state.origin, state.destination, queryDate, windowStart, windowEnd);
    bh.consume(journeys);
  }
}
