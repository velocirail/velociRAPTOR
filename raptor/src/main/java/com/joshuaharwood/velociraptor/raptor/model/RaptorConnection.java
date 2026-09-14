package com.joshuaharwood.velociraptor.raptor.model;

public sealed interface RaptorConnection {
  record TransferLeg(Stop origin, Stop destination, int duration, int startTime, int endTime)
          implements RaptorConnection {
  }

  record Connection(Trip trip, int startIndex, int endIndex) implements RaptorConnection {
  }
}
