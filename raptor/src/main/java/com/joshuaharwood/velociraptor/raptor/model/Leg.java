package com.joshuaharwood.velociraptor.raptor.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface Leg {
  Stop origin();

  Stop destination();

  record TimetableLeg(Stop origin, Stop destination, List<StopTime> stopTimes, Trip trip) implements Leg {
  }

  record TransferLeg(Stop origin, Stop destination, int duration, int startTime, int endTime, int originInterchange, int destinationInterchange, @Nullable String mode) implements Leg,
    ResultConnectionIndex {
    public TransferLeg(Stop origin, Stop destination, int duration, int startTime, int endTime) {
      this(origin, destination, duration, startTime, endTime, 0, 0, null);
    }
  }
}
