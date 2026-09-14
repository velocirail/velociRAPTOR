package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;

import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.GTFS_SOURCE_PATH;

@ApplicationScoped
public class PathGtfsLoader implements GtfsLoader {
  private final String path;

  @Inject
  public PathGtfsLoader(@ConfigProperty(name = GTFS_SOURCE_PATH) String path) {
    this.path = path;
  }

  @Override
  public ExtendedGtfsRelationalDaoImpl load() {
    return GtfsDeserialiser.createNewDao(new File(path));
  }
}
