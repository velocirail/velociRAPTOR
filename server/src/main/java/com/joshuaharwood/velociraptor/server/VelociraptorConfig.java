package com.joshuaharwood.velociraptor.server;

public final class VelociraptorConfig {
  public static final String ROUTING_ALGORITHM_PRECOMPUTE = "velociraptor.raptor.servicedate.precompute";
  public static final String GTFS_SOURCE_PATH = "velociraptor.gtfs.source.path";

  // Where a journey may use a fixed link (walk, Tube, ferry...). All default to true: fixed links
  // only between two trains. Env vars: VELOCIRAPTOR_RAPTOR_FIXEDLINKS_FORBIDLEADING etc.
  public static final String FIXED_LINKS_FORBID_LEADING = "velociraptor.raptor.fixedlinks.forbidleading";
  public static final String FIXED_LINKS_FORBID_TRAILING = "velociraptor.raptor.fixedlinks.forbidtrailing";
  public static final String FIXED_LINKS_FORBID_CONTIGUOUS = "velociraptor.raptor.fixedlinks.forbidcontiguous";

  private VelociraptorConfig() {
  }
}
