package com.joshuaharwood.velociraptor.server;

public final class OpenTelemetryConfigNames {

  public static final String SCOPE_NAME = "velociraptor";
  public static final String ROUTING_JOURNEYS_RETURNED = "raptor.journeys.returned";
  public static final String RAPTOR_CACHE_MISS = "raptor.servicedate.cache.miss";
  public static final String RAPTOR_CACHE_SIZE = "raptor.servicedate.cache.size";

  private OpenTelemetryConfigNames() {
  }
}
