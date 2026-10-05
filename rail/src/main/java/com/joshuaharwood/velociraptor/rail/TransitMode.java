package com.joshuaharwood.velociraptor.rail;

/**
 * What a trip runs as, from its route's GTFS {@code route_type}. gb-transit writes the seven basic types for
 * everything but a rail replacement bus ({@code 714}) and an air service ({@code 1100}), which take the extended
 * types, so a bus standing in for a train can be told apart from the train.
 */
public enum TransitMode {
  /** 0: tram, streetcar or light rail. */
  TRAM,
  /** 1: subway or metro - the Tube. */
  SUBWAY,
  /** 2: rail. */
  RAIL,
  /** 3: bus. */
  BUS,
  /** 4: ferry. */
  FERRY,
  /** 5: cable tram. */
  CABLE_TRAM,
  /** 6: aerial lift or gondola - the IFS Cloud Cable Car. */
  GONDOLA,
  /** 7: funicular. */
  FUNICULAR,
  /** 714: a bus replacing a train. */
  REPLACEMENT_BUS,
  /** 1100: air service. */
  AIR,
  /** Any other {@code route_type}. */
  OTHER;

  /** The mode of a GTFS {@code route_type}, basic or extended. */
  public static TransitMode fromGtfs(int routeType) {
    return switch (routeType) {
      case 0 -> TRAM;
      case 1 -> SUBWAY;
      case 2 -> RAIL;
      case 3 -> BUS;
      case 4 -> FERRY;
      case 5 -> CABLE_TRAM;
      case 6 -> GONDOLA;
      case 7 -> FUNICULAR;
      case 714 -> REPLACEMENT_BUS;
      case 1100 -> AIR;
      default -> OTHER;
    };
  }
}
