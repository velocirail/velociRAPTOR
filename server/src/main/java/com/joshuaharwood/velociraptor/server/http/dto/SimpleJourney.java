package com.joshuaharwood.velociraptor.server.http.dto;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param departureTime when the journey leaves the origin; for a leading fixed link, the time the
 *                      link has to be started to make the first train
 * @param arrivalTime   when the journey reaches the destination, including a trailing fixed link
 * @param duration      arrival minus departure, serialised as an ISO 8601 duration ({@code PT1H58M})
 * @param changes       changes of train: one fewer than the number of train legs
 */
public record SimpleJourney(OffsetDateTime departureTime,
                            OffsetDateTime arrivalTime,
                            Duration duration,
                            int changes,
                            List<SimpleLeg> legs) {
}
