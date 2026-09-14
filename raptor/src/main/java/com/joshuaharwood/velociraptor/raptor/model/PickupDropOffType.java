package com.joshuaharwood.velociraptor.raptor.model;

/**
 * GTFS {@code pickup_type} / {@code drop_off_type}: how a passenger may board or alight at a call.
 * Only {@link #NONE} forbids it; the other three all allow it, with more or less ceremony. The UK
 * feeds mark request stops {@link #COORDINATE_WITH_DRIVER}.
 */
public enum PickupDropOffType {
  /** 0: regularly scheduled. */
  REGULAR,
  /** 1: not available. */
  NONE,
  /** 2: must phone the agency to arrange. */
  PHONE_AGENCY,
  /** 3: must coordinate with the driver - a request stop. */
  COORDINATE_WITH_DRIVER;

  /** The GTFS code; an empty field reads as 0, and anything outside 0-3 is treated as regular. */
  public static PickupDropOffType fromGtfs(int code) {
    return switch (code) {
      case 1 -> NONE;
      case 2 -> PHONE_AGENCY;
      case 3 -> COORDINATE_WITH_DRIVER;
      default -> REGULAR;
    };
  }

  /** Whether a passenger may board (for a pickup type) or alight (for a drop-off type) here. */
  public boolean allowed() {
    return this != NONE;
  }
}
