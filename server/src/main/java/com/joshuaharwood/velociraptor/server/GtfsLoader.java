package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;

@FunctionalInterface
public interface GtfsLoader {
  ExtendedGtfsRelationalDaoImpl load();
}
