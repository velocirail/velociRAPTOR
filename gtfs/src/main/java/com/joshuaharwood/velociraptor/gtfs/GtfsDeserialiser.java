package com.joshuaharwood.velociraptor.gtfs;

import java.io.File;

public class GtfsDeserialiser {
  private static final String DEFAULT_AGENCY = "NR";

  private GtfsDeserialiser() {
  }

  public static ExtendedGtfsRelationalDaoImpl createNewDao(File gtfsFile) {
    ExtendedGtfsRelationalDaoImpl dao = new ExtendedGtfsRelationalDaoImpl();

    try {
      ExtendedGtfsReader gtfsReader = new ExtendedGtfsReader();

      gtfsReader.setInputLocation(gtfsFile);
      gtfsReader.setEntityStore(dao);
      gtfsReader.setDefaultAgencyId(DEFAULT_AGENCY);

      var defaultEntityClasses = gtfsReader.getEntityClasses();

      defaultEntityClasses.add(FixedLink.class);

      gtfsReader.run();

      return dao;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
