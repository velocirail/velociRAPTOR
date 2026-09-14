package com.joshuaharwood.velociraptor.raptor.benchmarks;

import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.obabridge.RaptorAlgorithmFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Measures {@code createFromDao} - the cost paid at {@code @Startup} and informs warmup planning. Uses its own state
 * because it needs a fresh DAO each trial but only re-loads once (the DAO load itself isn't what we're measuring).
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(2)
public class FactoryBuildBenchmark {

  @State(Scope.Benchmark)
  public static class DaoState {
    @Param("2026-06-03")
    public String serviceDateIso;

    ExtendedGtfsRelationalDaoImpl dao;
    CalendarService calendarService;

    ServiceDate serviceDate() {
      String[] parts = serviceDateIso.split("-");
      return new ServiceDate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Setup(Level.Trial)
    public void load() {
      String override = System.getProperty("raptor.benchmark.gtfs");
      File gtfsFile = override != null
        ? new File(override)
        : new File("fixtures/gtfs-sample");
      dao = GtfsDeserialiser.createNewDao(gtfsFile);
      calendarService = CalendarServiceDataFactoryImpl.createService(dao);
    }
  }

  @Benchmark
  public void createFromDao(DaoState state, Blackhole bh) {
    var raptor = RaptorAlgorithmFactory.createFromDao(state.dao, state.calendarService,
      state.serviceDate());
    bh.consume(raptor);
  }
}
