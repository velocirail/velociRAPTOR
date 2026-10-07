package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.FeedFormat;

@FunctionalInterface
public interface GtfsLoader {
  LoadedFeed load(FeedFormat format);
}
