package com.joshuaharwood.velociraptor.server;

import org.jspecify.annotations.Nullable;

/** How the server reads a feed's optional text fields. */
final class FeedValues {

  private FeedValues() {
  }

  /** A GTFS field that is absent or blank gives no value: null rather than an empty string. */
  static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
