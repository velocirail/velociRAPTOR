package com.joshuaharwood.velociraptor.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;

@ApplicationScoped
public class CalendarServiceProducer {
    private final ExtendedGtfsRelationalDaoImpl dao;

    public CalendarServiceProducer(ExtendedGtfsRelationalDaoImpl dao) {
        this.dao = dao;
    }

    @Produces
    @ApplicationScoped
    CalendarService produce() {
        return CalendarServiceDataFactoryImpl.createService(dao);
    }
}
