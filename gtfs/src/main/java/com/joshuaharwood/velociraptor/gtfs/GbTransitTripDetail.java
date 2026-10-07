package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.csv_entities.schema.annotations.CsvFields;
import org.onebusaway.gtfs.model.IdentityBean;

/**
 * A row of a gb-transit {@code trips.txt} with the columns OneBusAway's {@code Trip} drops: the headcode and the
 * traction gb-transit takes from each schedule's BS record. Read beside {@code Trip} from the same file, only to be
 * turned into a {@link TrainDetail} per trip, as {@link GbTransitTransfer} is read to find the fixed links. Every
 * column but the trip id is optional, so a feed built before gb-transit published them reads as having none.
 */
@SuppressWarnings("unused")
@NullUnmarked
@CsvFields(filename = "trips.txt")
public class GbTransitTripDetail extends IdentityBean<Integer> {
  @CsvField(ignore = true)
  private int id;

  @CsvField(name = "trip_id")
  private String tripId;

  @CsvField(optional = true)
  private String headcode;

  @CsvField(name = "power_type", optional = true)
  private String powerType;

  @CsvField(name = "timing_load", optional = true)
  private String timingLoad;

  @CsvField(name = "max_speed", optional = true)
  private String maxSpeed;

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  public String getTripId() {
    return tripId;
  }

  public void setTripId(String tripId) {
    this.tripId = tripId;
  }

  public String getHeadcode() {
    return headcode;
  }

  public void setHeadcode(String headcode) {
    this.headcode = headcode;
  }

  public String getPowerType() {
    return powerType;
  }

  public void setPowerType(String powerType) {
    this.powerType = powerType;
  }

  public String getTimingLoad() {
    return timingLoad;
  }

  public void setTimingLoad(String timingLoad) {
    this.timingLoad = timingLoad;
  }

  public String getMaxSpeed() {
    return maxSpeed;
  }

  public void setMaxSpeed(String maxSpeed) {
    this.maxSpeed = maxSpeed;
  }

  /** The row as a {@link TrainDetail}: blanks are null, and a speed that is not a number is no speed. */
  TrainDetail toTrainDetail() {
    return new TrainDetail(blankToNull(headcode), blankToNull(powerType), blankToNull(timingLoad), speed(maxSpeed));
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static @Nullable Integer speed(@Nullable String value) {
    var text = blankToNull(value);
    if (text == null) {
      return null;
    }
    try {
      return Integer.valueOf(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
