package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.GTFS_SOURCE_FORMAT;
import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.GTFS_SOURCE_PATH;

@Startup
@ApplicationScoped
public class GtfsDaoProducer {
  private final String gtfsSourcePath;
  private final FeedFormat feedFormat;
  private final S3GtfsLoader s3Loader;
  private final PathGtfsLoader pathLoader;
  private final DoubleHistogram loadDuration;
  private ExtendedGtfsRelationalDaoImpl dao;

  @Inject
  public GtfsDaoProducer(@ConfigProperty(name = GTFS_SOURCE_PATH) String gtfsSourcePath,
                         // Required: there is no default format, so a server pointed at a feed says which it is.
                         @ConfigProperty(name = GTFS_SOURCE_FORMAT) String gtfsSourceFormat,
                         S3GtfsLoader s3Loader,
                         PathGtfsLoader pathLoader,
                         @SuppressWarnings("CdiInjectionPointsInspection") OpenTelemetry openTelemetry) {
    this.gtfsSourcePath = gtfsSourcePath;
    this.feedFormat = FeedFormat.fromConfigValue(gtfsSourceFormat);
    this.s3Loader = s3Loader;
    this.pathLoader = pathLoader;
    Meter meter = openTelemetry.getMeter("velociraptor");
    loadDuration = meter.histogramBuilder("gtfs.load.seconds")
                        .setDescription("Time to deserialise and load GTFS data into the DAO")
                        .build();
  }

  @PostConstruct
  void load() {
    GtfsLoader loader = gtfsSourcePath.startsWith("s3://") ? s3Loader : pathLoader;
    Log.infof("Building GTFS cache from %s (%s feed)...", gtfsSourcePath.startsWith("s3://") ? "S3" : "path", feedFormat);
    long start = System.nanoTime();
    dao = loader.load(feedFormat);
    dao.initialise();
    loadDuration.record((System.nanoTime() - start) / 1e9);
    Log.info("GTFS cache built.");
  }

  @Produces
  @ApplicationScoped
  public ExtendedGtfsRelationalDaoImpl produce() {
    return dao;
  }
}
