package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.rail.AssociationType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where a leg stays aboard as its train divides, joins another or runs on as the next service: a passenger does
 * not change, but on a divide must be in the right portion.
 *
 * @param stop          where it happens
 * @param type          {@code DIVIDE}, {@code JOIN} or {@code NEXT}
 * @param service       the train from here on
 * @param headsign      where the train goes from here, which on a divide is the portion to be in; {@code null}
 *                      where the feed gives none
 * @param otherHeadsigns on a divide, where each other portion goes; otherwise empty
 */
@Schema(requiredProperties = {"stop", "type", "service", "headsign", "otherHeadsigns"})
public record Association(String stop,
                          AssociationType type,
                          TrainService service,
                          @Schema(nullable = true) @Nullable String headsign,
                          List<String> otherHeadsigns) {
}
