package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param departureTime when the journey leaves the origin: its first leg's departure, which for a leading fixed
 *                      link is the latest start that still makes the first train
 * @param arrivalTime   when the journey reaches the destination: its last leg's arrival, which for a trailing
 *                      fixed link is when the link ends after the last train
 * @param duration      arrival minus departure, serialised as an ISO 8601 duration ({@code PT1H58M})
 * @param changes       changes of train: one fewer than the number of train legs
 */
@Schema(requiredProperties = {"origin", "destination", "departureTime", "arrivalTime", "duration", "changes", "legs"})
public record RailJourney(String origin,
                          String destination,
                          OffsetDateTime departureTime,
                          OffsetDateTime arrivalTime,
                          Duration duration,
                          int changes,
                          List<RailJourneyLeg> legs) {
}
