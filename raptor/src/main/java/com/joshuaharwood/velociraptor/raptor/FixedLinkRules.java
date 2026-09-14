package com.joshuaharwood.velociraptor.raptor;

/**
 * Where a journey may use a fixed link (a walk, a Tube ride, a ferry - any {@code links.txt}
 * connection that is not a timetabled train). The rules are enforced inside the scan, not by
 * filtering results: a fixed link that is not allowed must also never set a label, or it would
 * prune the train arrival that should be returned in its place.
 * <p>
 * The rules restrict the journey space, and the scan keeps one label per stop and round, so one
 * combination is out of reach: with {@code forbidContiguous} on, a stop whose label in a round was
 * set by a fixed link cannot be left by a fixed link in the next round even if a train also reached
 * it in that same round, a little later than the link. The train arrival was not the label, so it
 * has no connection to continue from. Lifting that needs a second, trip-only label per stop.
 *
 * @param forbidLeading    a journey may not begin with a fixed link: none is relaxed from an origin
 *                         in round 1
 * @param forbidTrailing   a journey may not end with a fixed link: none is relaxed into the
 *                         destination
 * @param forbidContiguous a journey may not take two fixed links in a row (delay repay does not
 *                         cover that): none is relaxed from a stop whose previous-round label was
 *                         itself set by a fixed link
 */
public record FixedLinkRules(boolean forbidLeading, boolean forbidTrailing, boolean forbidContiguous) {

  /** No restriction: the plain RAPTOR journey space, and the default for library callers. */
  public static final FixedLinkRules NONE = new FixedLinkRules(false, false, false);

  /** Every rule on: fixed links only between two trains. */
  public static final FixedLinkRules ALL = new FixedLinkRules(true, true, true);
}
