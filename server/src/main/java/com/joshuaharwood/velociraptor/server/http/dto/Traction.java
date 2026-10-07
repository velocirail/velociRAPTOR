package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * What one source says hauls the train. A train can carry several, one per source, and they may disagree: the
 * server passes each on as its source states it and leaves the choice of which to trust to the consumer.
 *
 * @param source     where this came from. {@code CIF_SCHEDULE}: the traction the train's CIF schedule (its BS record)
 *                   plans, as published in the GTFS feed - what was timetabled, not necessarily what turns up
 * @param powerType  the CIF power type: {@code D}, {@code DEM}, {@code DMU}, {@code E}, {@code ED}, {@code EML},
 *                   {@code EMU} or {@code HST}
 * @param timingLoad read by the power type: the class of a multiple unit ({@code 387}), a trailing load in tonnes
 *                   behind a locomotive
 * @param maxSpeed   the maximum speed, in mph
 */
@Schema(requiredProperties = {"source", "powerType", "timingLoad", "maxSpeed"})
public record Traction(@Schema(enumeration = {Traction.CIF_SCHEDULE}) String source,
                       @Schema(nullable = true) @Nullable String powerType,
                       @Schema(nullable = true) @Nullable String timingLoad,
                       @Schema(nullable = true) @Nullable Integer maxSpeed) {
  public static final String CIF_SCHEDULE = "CIF_SCHEDULE";
}
