package com.joshuaharwood.velociraptor.raptor.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Hot-path benchmark: a single {@code raptor.scan(origins)} call. This isolates the core routing primitive - the
 * target of primitive-collections, int-indexed stops, and ThreadLocal-scratch-buffer optimisations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class ScanBenchmark {

  @Benchmark
  public void scanFromOrigin(GtfsState state, Blackhole bh) {
    var result = state.raptor.scan(Map.of(state.origin, state.departTime));
    bh.consume(result);
  }
}
