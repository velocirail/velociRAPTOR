package com.joshuaharwood.velociraptor.raptor.benchmarks;

import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.obabridge.RaptorAlgorithmFactory;

import java.io.File;
import java.time.LocalTime;

/**
 * Shared benchmark fixture: loads the GTFS dataset once per trial and constructs a {@link RaptorAlgorithm} for a
 * fixed service date.
 *
 * Override the dataset via system property {@code raptor.benchmark.gtfs} (absolute path). Defaults to the
 * artificial sample feed in fixtures/gtfs-sample, resolved relative to the current working directory.
 */
@State(Scope.Benchmark)
public class GtfsState {

  @Param("2026-06-03")
  public String serviceDateIso;

  public RaptorAlgorithm raptor;
  public ExtendedGtfsRelationalDaoImpl dao;
  public CalendarService calendarService;

  public Stop origin = new Stop("BTN");
  public Stop destination = new Stop("VIC");
  public int departTime = LocalTime.of(13, 0).toSecondOfDay();

  @Setup(Level.Trial)
  public void load() {
    dao = GtfsDeserialiser.createNewDao(resolveGtfsFile());
    calendarService = CalendarServiceDataFactoryImpl.createService(dao);
    raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, parseServiceDate());
  }

  public ServiceDate parseServiceDate() {
    String[] parts = serviceDateIso.split("-");
    return new ServiceDate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
  }

  private static File resolveGtfsFile() {
    String override = System.getProperty("raptor.benchmark.gtfs");
    if (override != null) {
      return new File(override);
    }
    return new File("fixtures/gtfs-sample");
  }
}
