package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedFormat;

@FunctionalInterface
public interface GtfsLoader {
  ExtendedGtfsRelationalDaoImpl load(FeedFormat format);
}
