package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.Nullable;

/**
 * What a gb-transit feed says about a train beyond its timetable, from the columns it adds to {@code trips.txt}: the
 * headcode and the traction the CIF schedule's BS record plans for it. Each is null where the feed leaves it blank or
 * does not have the column at all, as a feed built before gb-transit published them does not.
 *
 * @param headcode   the train identity, such as {@code 1A23}
 * @param powerType  the CIF power type: {@code D}, {@code DEM}, {@code DMU}, {@code E}, {@code ED}, {@code EML},
 *                   {@code EMU} or {@code HST}
 * @param timingLoad read by the power type: the class of a multiple unit ({@code 387}), a trailing load in tonnes
 *                   behind a locomotive
 * @param maxSpeed   the planned maximum speed, in mph
 */
public record TrainDetail(@Nullable String headcode,
                          @Nullable String powerType,
                          @Nullable String timingLoad,
                          @Nullable Integer maxSpeed) {

  public static final TrainDetail NONE = new TrainDetail(null, null, null, null);

  /** Whether the schedule says anything about its traction. */
  public boolean hasTraction() {
    return powerType != null || timingLoad != null || maxSpeed != null;
  }
}
