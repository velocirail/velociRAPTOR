package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where a through trip stays aboard from one train onto another. The trip's k-th association is between its k-th
 * and (k+1)-th {@link TrainRun}.
 *
 * @param stopIndex      the call where it happens, by the trip's stop-time index
 * @param headsign       where the train goes from here on, which for a {@link AssociationType#DIVIDE} is the portion
 *                       to be in; null where the feed gives none
 * @param otherHeadsigns for a {@link AssociationType#DIVIDE}, where each other portion goes; otherwise empty
 */
public record Association(int stopIndex,
                          AssociationType type,
                          @Nullable String headsign,
                          List<String> otherHeadsigns) {
}
