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
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end benchmark: single-origin to single-destination plan including {@link JourneyFactory} result
 * construction. Represents the typical HTTP request shape.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class DepartAfterQueryBenchmark {

  private DepartAfterQuery<?> query;
  private LocalDate queryDate;

  @Setup
  public void setup(GtfsState state) {
    this.query = new DepartAfterQuery<>(state.raptor, new JourneyFactory());
    var sd = state.parseServiceDate();
    this.queryDate = LocalDate.of(sd.getYear(), sd.getMonth(), sd.getDay());
  }

  @Benchmark
  public void plan(GtfsState state, Blackhole bh) {
    var journeys = query.plan(state.origin, state.destination, queryDate, state.departTime);
    bh.consume(journeys);
  }
}
