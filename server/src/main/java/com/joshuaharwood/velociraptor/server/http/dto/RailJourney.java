package com.joshuaharwood.velociraptor.server.http.dto;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param departureTime when the journey leaves the origin: the first train's departure, or for a
 *                      leading fixed link that departure less the link and the interchange at its
 *                      far end
 * @param arrivalTime   when the journey reaches the destination: the last train's arrival, or for a
 *                      trailing fixed link that arrival plus the interchange at its start and the link
 * @param duration      arrival minus departure, serialised as an ISO 8601 duration ({@code PT1H58M})
 * @param changes       changes of train: one fewer than the number of train legs
 */
public record RailJourney(String origin,
                          String destination,
                          @Nullable OffsetDateTime departureTime,
                          @Nullable OffsetDateTime arrivalTime,
                          @Nullable Duration duration,
                          int changes,
                          List<RailJourneyLeg> legs) {
}
