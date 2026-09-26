package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.NullUnmarked;
import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.csv_entities.schema.annotations.CsvFields;
import org.onebusaway.gtfs.model.IdentityBean;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.serialization.mappings.EntityFieldMappingFactory;

/**
 * A row of a gb-transit {@code transfers.txt} with the columns OneBusAway's {@code Transfer} drops: the mode, time
 * window, dates and days a fixed link carries. Read beside {@code Transfer} from the same file, and only to find the
 * fixed links in it; every column but the stops is kept as text because each may be empty.
 */
@SuppressWarnings("unused")
@NullUnmarked
@CsvFields(filename = "transfers.txt")
public class GbTransitTransfer extends IdentityBean<Integer> {
  @CsvField(ignore = true)
  private int id;

  @CsvField(name = "from_stop_id", mapping = EntityFieldMappingFactory.class)
  private Stop fromStop;

  @CsvField(name = "to_stop_id", mapping = EntityFieldMappingFactory.class)
  private Stop toStop;

  @CsvField(name = "from_trip_id", optional = true)
  private String fromTripId;

  @CsvField(name = "to_trip_id", optional = true)
  private String toTripId;

  @CsvField(name = "transfer_type", optional = true)
  private String transferType;

  @CsvField(name = "min_transfer_time", optional = true)
  private String minTransferTime;

  @CsvField(optional = true)
  private String mode;

  @CsvField(name = "start_time", optional = true)
  private String startTime;

  @CsvField(name = "end_time", optional = true)
  private String endTime;

  @CsvField(name = "start_date", optional = true)
  private String startDate;

  @CsvField(name = "end_date", optional = true)
  private String endDate;

  @CsvField(optional = true)
  private String monday;

  @CsvField(optional = true)
  private String tuesday;

  @CsvField(optional = true)
  private String wednesday;

  @CsvField(optional = true)
  private String thursday;

  @CsvField(optional = true)
  private String friday;

  @CsvField(optional = true)
  private String saturday;

  @CsvField(optional = true)
  private String sunday;

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  public Stop getFromStop() {
    return fromStop;
  }

  public void setFromStop(Stop fromStop) {
    this.fromStop = fromStop;
  }

  public Stop getToStop() {
    return toStop;
  }

  public void setToStop(Stop toStop) {
    this.toStop = toStop;
  }

  public String getFromTripId() {
    return fromTripId;
  }

  public void setFromTripId(String fromTripId) {
    this.fromTripId = fromTripId;
  }

  public String getToTripId() {
    return toTripId;
  }

  public void setToTripId(String toTripId) {
    this.toTripId = toTripId;
  }

  public String getTransferType() {
    return transferType;
  }

  public void setTransferType(String transferType) {
    this.transferType = transferType;
  }

  public String getMinTransferTime() {
    return minTransferTime;
  }

  public void setMinTransferTime(String minTransferTime) {
    this.minTransferTime = minTransferTime;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setEndTime(String endTime) {
    this.endTime = endTime;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public String getMonday() {
    return monday;
  }

  public void setMonday(String monday) {
    this.monday = monday;
  }

  public String getTuesday() {
    return tuesday;
  }

  public void setTuesday(String tuesday) {
    this.tuesday = tuesday;
  }

  public String getWednesday() {
    return wednesday;
  }

  public void setWednesday(String wednesday) {
    this.wednesday = wednesday;
  }

  public String getThursday() {
    return thursday;
  }

  public void setThursday(String thursday) {
    this.thursday = thursday;
  }

  public String getFriday() {
    return friday;
  }

  public void setFriday(String friday) {
    this.friday = friday;
  }

  public String getSaturday() {
    return saturday;
  }

  public void setSaturday(String saturday) {
    this.saturday = saturday;
  }

  public String getSunday() {
    return sunday;
  }

  public void setSunday(String sunday) {
    this.sunday = sunday;
  }

  @Override
  public String toString() {
    return "<GbTransitTransfer " + fromStop + " -> " + toStop + ">";
  }
}
