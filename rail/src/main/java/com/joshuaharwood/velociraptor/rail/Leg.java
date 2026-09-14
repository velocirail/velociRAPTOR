package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;
import com.joshuaharwood.velociraptor.raptor.model.Stop;

import java.time.Duration;
import java.time.LocalDateTime;

public sealed interface Leg {

  String type();

  Stop origin();

  Stop destination();

  LocalDateTime departureTime();

  LocalDateTime arrivalTime();

  /** Arrival minus departure. */
  default Duration duration() {
    return Duration.between(departureTime(), arrivalTime());
  }

  record FixedLink(Stop origin,
                   Stop destination,
                   LocalDateTime departureTime,
                   LocalDateTime arrivalTime,
                   int durationSeconds,
                   int originInterchange,
                   int destinationInterchange,
                   @Nullable String mode) implements Leg {
    @Override
    public String type() {
      return "FIXED_LEG";
    }
  }

  record RailLeg(
          Stop origin,
          Stop destination,
          LocalDateTime departureTime,
          LocalDateTime arrivalTime,
          @Nullable String originTrainUid,
          @Nullable String destinationTrainUid,
          TrainTrip trainTrip,
          int startIndex,
          int endIndex
  ) implements Leg {
    @Override
    public String type() {
      return "RAIL_LEG";
    }
  }
}
