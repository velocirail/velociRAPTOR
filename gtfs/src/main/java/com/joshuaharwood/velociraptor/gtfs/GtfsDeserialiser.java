package com.joshuaharwood.velociraptor.gtfs;

import org.onebusaway.csv_entities.exceptions.MissingRequiredEntityException;
import org.onebusaway.gtfs.model.Area;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopAreaElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class GtfsDeserialiser {
  private static final Logger LOGGER = LoggerFactory.getLogger(GtfsDeserialiser.class);
  private static final String DEFAULT_AGENCY = "NR";

  /**
   * Files gb-transit publishes that routing has no use for. {@code shapes.txt} is the big one: a line through every
   * station each of 13,000-odd shapes touches.
   */
  private static final List<Class<?>> GB_TRANSIT_UNUSED = List.of(ShapePoint.class, Area.class, StopAreaElement.class);

  private GtfsDeserialiser() {
  }

  /**
   * Reads a GTFS zip or directory into a DAO whose {@link ExtendedGtfsRelationalDaoImpl#feedProfile() profile}
   * says how its stops and trips map onto the routing model.
   *
   * @throws IllegalStateException if the feed is plainly not in {@code format}
   */
  @SuppressWarnings("removal") // DTD2GTFS is deprecated for removal; reading it is still supported until then
  public static ExtendedGtfsRelationalDaoImpl createNewDao(File gtfsFile, FeedFormat format) {
    if (format == FeedFormat.DTD2GTFS) {
      LOGGER.warn("Reading {} as a dtd2gtfs feed. That format is deprecated and will be removed: "
        + "use a gb-transit feed (https://github.com/planarnetwork/gb-transit) and the gb-transit format instead.", gtfsFile);
    }

    ExtendedGtfsRelationalDaoImpl dao = new ExtendedGtfsRelationalDaoImpl();

    try {
      ExtendedGtfsReader gtfsReader = new ExtendedGtfsReader();

      gtfsReader.setInputLocation(gtfsFile);
      gtfsReader.setEntityStore(dao);
      gtfsReader.setDefaultAgencyId(DEFAULT_AGENCY);

      var entityClasses = gtfsReader.getEntityClasses();

      switch (format) {
        case GB_TRANSIT -> {
          entityClasses.removeAll(GB_TRANSIT_UNUSED);
          entityClasses.add(GbTransitTransfer.class);
        }
        case DTD2GTFS -> entityClasses.add(FixedLink.class);
      }

      gtfsReader.run();
    } catch (MissingRequiredEntityException e) {
      if (format == FeedFormat.DTD2GTFS && FixedLink.class.equals(e.getEntityType())) {
        throw new IllegalStateException(gtfsFile + " was read as a " + FeedFormat.DTD2GTFS + " feed but has no links.txt, "
          + "which every dtd2gtfs feed has; a " + FeedFormat.GB_TRANSIT + " feed keeps its fixed links in transfers.txt.", e);
      }
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    checkShape(dao, format, gtfsFile);

    if (format == FeedFormat.GB_TRANSIT) {
      var profile = GbTransitProfile.of(dao.getAllStops());
      var links = GbTransitFixedLinks.from(dao.getAllEntitiesForType(GbTransitTransfer.class), profile);
      dao.clearAllEntitiesForType(GbTransitTransfer.class);
      links.forEach(dao::saveEntity);
      dao.setFeedProfile(profile);
      LOGGER.info("Read {} fixed links from transfers.txt", links.size());
    }

    return dao;
  }

  /**
   * gb-transit always has stations ({@code location_type} 1) with the calls at boarding points beneath them;
   * dtd2gtfs never does. Reading one as the other does not fail by itself - it routes over the wrong stops - so
   * refuse it here.
   */
  @SuppressWarnings("removal")
  private static void checkShape(ExtendedGtfsRelationalDaoImpl dao, FeedFormat format, File gtfsFile) {
    boolean hasStations = dao.getAllStops().stream().anyMatch(stop -> stop.getLocationType() == Stop.LOCATION_TYPE_STATION);
    if (format == FeedFormat.GB_TRANSIT && !hasStations) {
      throw new IllegalStateException(gtfsFile + " was read as a " + FeedFormat.GB_TRANSIT + " feed but has no stations "
        + "(stops with location_type 1). A gb-transit feed calls at boarding points under a station; this looks like a "
        + FeedFormat.DTD2GTFS + " feed.");
    }
    if (format == FeedFormat.DTD2GTFS && hasStations) {
      throw new IllegalStateException(gtfsFile + " was read as a " + FeedFormat.DTD2GTFS + " feed but has stations "
        + "(stops with location_type 1), which only a " + FeedFormat.GB_TRANSIT + " feed has.");
    }
  }
}
