package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;

/** A feed read into memory, and where it was read from. */
public record LoadedFeed(ExtendedGtfsRelationalDaoImpl dao, FeedSource source) {
}
