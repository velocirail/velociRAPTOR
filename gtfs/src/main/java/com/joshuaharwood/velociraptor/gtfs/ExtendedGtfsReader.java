package com.joshuaharwood.velociraptor.gtfs;

import org.onebusaway.gtfs.serialization.EntityReferenceNotFoundException;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ExtendedGtfsReader extends GtfsReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedGtfsReader.class);

  @Override
  protected String getAgencyForEntity(Class<?> entityType, String entityId) {
    try {
      return super.getAgencyForEntity(entityType, entityId);
    } catch (EntityReferenceNotFoundException e) {
      LOGGER.warn("Could not find entity with given ID. [Entity type: {}] [Entity ID: {}]", entityType, entityId, e);
      return getDefaultAgencyId();
    }
  }
}
