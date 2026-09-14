package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.NullUnmarked;
import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.csv_entities.schema.annotations.CsvFields;
import org.onebusaway.gtfs.model.IdentityBean;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.mappings.EntityFieldMappingFactory;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;

import java.time.LocalDate;

@SuppressWarnings("unused")
@NullUnmarked
@CsvFields(filename = "links.txt")
public class FixedLink extends IdentityBean<Integer> {
  @CsvField(ignore = true)
  private int id;

  @CsvField(name = "from_stop_id", mapping = EntityFieldMappingFactory.class)
  private Stop fromStop;

  @CsvField(name = "to_stop_id", mapping = EntityFieldMappingFactory.class)
  private Stop toStop;

  @CsvField(defaultValue = "UNKNOWN_MODE")
  private String mode;

  @CsvField(name = "duration")
  private int durationInSeconds;

  @CsvField(name = "start_time", mapping = StopTimeFieldMappingFactory.class)
  private int startTime;

  @CsvField(name = "end_time", mapping = StopTimeFieldMappingFactory.class)
  private int endTime;

  @CsvField(name = "start_date", mapping = DashedServiceDateFieldMappingFactory.class)
  private ServiceDate startDate;

  @CsvField(name = "end_date", mapping = DashedServiceDateFieldMappingFactory.class)
  private ServiceDate endDate;

  @CsvField
  private boolean monday;

  @CsvField
  private boolean tuesday;

  @CsvField
  private boolean wednesday;

  @CsvField
  private boolean thursday;

  @CsvField
  private boolean friday;

  @CsvField
  private boolean saturday;

  @CsvField
  private boolean sunday;

  public FixedLink() {
  }

  public FixedLink(FixedLink link) {
    this.id = link.id;
    this.fromStop = link.fromStop;
    this.toStop = link.toStop;
    this.mode = link.mode;
    this.durationInSeconds = link.durationInSeconds;
    this.startTime = link.startTime;
    this.endTime = link.endTime;
    this.startDate = link.startDate;
    this.endDate = link.endDate;
    this.monday = link.monday;
    this.tuesday = link.tuesday;
    this.wednesday = link.wednesday;
    this.thursday = link.thursday;
    this.friday = link.friday;
    this.saturday = link.saturday;
    this.sunday = link.sunday;
  }

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  public int getEndTime() {
    return endTime;
  }

  public void setEndTime(int endTime) {
    this.endTime = endTime;
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

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public int getDurationInSeconds() {
    return durationInSeconds;
  }

  public void setDurationInSeconds(int durationInSeconds) {
    this.durationInSeconds = durationInSeconds;
  }

  public int getStartTime() {
    return startTime;
  }

  public void setStartTime(int startTime) {
    this.startTime = startTime;
  }

  public ServiceDate getStartDate() {
    return startDate;
  }

  public void setStartDate(ServiceDate startDate) {
    this.startDate = startDate;
  }

  public ServiceDate getEndDate() {
    return endDate;
  }

  public void setEndDate(ServiceDate endDate) {
    this.endDate = endDate;
  }

  public boolean isMonday() {
    return monday;
  }

  public void setMonday(boolean monday) {
    this.monday = monday;
  }

  public boolean isTuesday() {
    return tuesday;
  }

  public void setTuesday(boolean tuesday) {
    this.tuesday = tuesday;
  }

  public boolean isWednesday() {
    return wednesday;
  }

  public void setWednesday(boolean wednesday) {
    this.wednesday = wednesday;
  }

  public boolean isThursday() {
    return thursday;
  }

  public void setThursday(boolean thursday) {
    this.thursday = thursday;
  }

  public boolean isFriday() {
    return friday;
  }

  public void setFriday(boolean friday) {
    this.friday = friday;
  }

  public boolean isSaturday() {
    return saturday;
  }

  public void setSaturday(boolean saturday) {
    this.saturday = saturday;
  }

  public boolean isSunday() {
    return sunday;
  }

  public void setSunday(boolean sunday) {
    this.sunday = sunday;
  }

  @Override
  public String toString() {
    return "<FixedLink " + getId() + ">";
  }

  public boolean runsOn(ServiceDate arg) {
    //todo: tz?
    var localDate = LocalDate.of(arg.getYear(), arg.getMonth(), arg.getDay()).getDayOfWeek();
    
    boolean runsOnDay = switch (localDate) {
      //todo: bad gen'd method names
      case MONDAY -> this.isMonday();
      case TUESDAY -> this.isTuesday();
      case WEDNESDAY -> this.isWednesday();
      case THURSDAY -> this.isThursday();
      case FRIDAY -> this.isFriday();
      case SATURDAY -> this.isSaturday();
      case SUNDAY -> this.isSunday();
    };
    
    if (!runsOnDay) {
      return false;
    }
    
    return arg.compareTo(getStartDate()) >= 0 && arg.compareTo(getEndDate()) <= 0;
  }
}
