package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.raptor.model.Trip;

import java.util.List;

/**
 * Two trips a passenger stays aboard from one onto the other: the in-seat transfer ({@code transfer_type} 4) a
 * gb-transit feed states a divide, a join or an onward working as. {@code from}'s call {@code fromIndex} is
 * {@code to}'s call {@code toIndex}, at the same station.
 *
 * @param dayOffset     the service days after {@code from}'s that {@code to} runs on: 1 where {@code from} reaches
 *                      the link after midnight and {@code to} leaves on the next day's timetable, otherwise 0
 * @param otherPortions for a {@link Type#DIVIDE}, every portion that goes elsewhere: {@code from} itself where it
 *                      carries on, or the other trips that start where it divides; empty otherwise
 */
public record TripLink(Trip from, Trip to, int fromIndex, int toIndex, int dayOffset, Type type,
                       List<Trip> otherPortions) {

  public enum Type {
    /** The train divides here and {@code to} is the portion that carries on to the passenger's destination. */
    DIVIDE,
    /** {@code from} joins {@code to} here, and the two run on as one train. */
    JOIN,
    /** {@code from} ends here and forms {@code to}: the train carries on under another identity. */
    NEXT
  }
}
