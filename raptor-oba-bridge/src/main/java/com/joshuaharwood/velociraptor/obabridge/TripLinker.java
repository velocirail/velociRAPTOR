package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;

import java.util.List;

/** Builds the trip a passenger rides by staying aboard across a {@link TripLink}. */
@FunctionalInterface
public interface TripLinker {

  /**
   * @param timetable the through trip's calls as the two trains make them: {@code from}'s up to the link, then
   *                  {@code to}'s after it, with the call at the link arriving as {@code from} does and departing as
   *                  {@code to} does
   * @param stopTimes the same calls as the trip is routed on: boarded only before the link and left only after it,
   *                  so the through trip carries only journeys that stay aboard across it, and every other journey
   *                  rides {@code from} or {@code to} itself
   */
  Trip link(TripLink link, List<StopTime> stopTimes, List<StopTime> timetable);
}
