package com.joshuaharwood.velociraptor.raptor.model;

import java.util.List;

public interface Trip {
  String id();

  List<StopTime> stopTimes();

  /**
   * Hot-path time accessors. The scan and {@link com.joshuaharwood.velociraptor.raptor.RouteScanner}
   * read times through these rather than {@link #stopTimes()} so a time offset (see {@code OffsetTrip})
   * can be applied as a pure int add - no per-trip {@link StopTime} allocation. Defaults delegate to
   * {@link #stopTimes()}; for a record-backed trip that is just a field read, so there is no overhead
   * versus the previous direct access.
   */
  default int arrivalTime(int stopIndex) {
    return stopTimes().get(stopIndex).arrivalTime();
  }

  default int departureTime(int stopIndex) {
    return stopTimes().get(stopIndex).departureTime();
  }

  default boolean canAlight(int stopIndex) {
    return stopTimes().get(stopIndex).canAlight();
  }

  default boolean canBoard(int stopIndex) {
    return stopTimes().get(stopIndex).canBoard();
  }
}
