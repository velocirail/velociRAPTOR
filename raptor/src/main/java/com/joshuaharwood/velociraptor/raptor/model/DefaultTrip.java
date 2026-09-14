package com.joshuaharwood.velociraptor.raptor.model;

import java.util.List;

public record DefaultTrip(String id, List<StopTime> stopTimes) implements Trip {
}
