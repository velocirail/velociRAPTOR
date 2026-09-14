package com.joshuaharwood.velociraptor.obabridge;

import module java.base;

import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.model.Stop;

public class Main {

  private static final ExtendedGtfsRelationalDaoImpl DAO = GtfsDeserialiser.createNewDao(new File(
    "fixtures/gtfs-sample"));

  private static final CalendarService CALENDAR_SERVICE = CalendarServiceDataFactoryImpl.createService(
    DAO);

  void main() {
    var serviceDate = new ServiceDate(2026, 6, 3);

    var nxRaptor = RaptorAlgorithmFactory.createFromDao(DAO, CALENDAR_SERVICE, serviceDate);

    Stop btn = new Stop("BTN");
    int start = LocalTime.of(13, 0).toSecondOfDay();

    var scan = nxRaptor.scan(Map.of(btn, start));

    System.out.println();
  }
}
