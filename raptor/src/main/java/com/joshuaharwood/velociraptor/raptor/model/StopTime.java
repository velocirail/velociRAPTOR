package com.joshuaharwood.velociraptor.raptor.model;

/**
 * One call of a trip. Times are seconds since the service date's midnight. {@code pickup} and
 * {@code dropOff} are the GTFS types, kept rather than reduced to booleans so results can say what
 * kind of stop the passenger boards or alights at; {@link #canBoard()} and {@link #canAlight()} are
 * what the scan reads.
 */
public record StopTime(Stop stop,
                       int arrivalTime,
                       int departureTime,
                       PickupDropOffType pickup,
                       PickupDropOffType dropOff) {

  /** A call with regular pickup/drop-off where allowed and none where not. */
  public StopTime(Stop stop, int arrivalTime, int departureTime, boolean canBoard, boolean canAlight) {
    this(stop, arrivalTime, departureTime,
         canBoard ? PickupDropOffType.REGULAR : PickupDropOffType.NONE,
         canAlight ? PickupDropOffType.REGULAR : PickupDropOffType.NONE);
  }

  public boolean canBoard() {
    return pickup.allowed();
  }

  public boolean canAlight() {
    return dropOff.allowed();
  }
}
