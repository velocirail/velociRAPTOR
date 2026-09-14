package com.joshuaharwood.velociraptor.gtfs;

import module java.base;
import module onebusaway.gtfs;
import module org.slf4j;

import com.google.common.collect.Sets;
import org.jspecify.annotations.Nullable;

public class ExtendedGtfsRelationalDaoImpl extends GtfsRelationalDaoImpl {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedGtfsRelationalDaoImpl.class);

  private final ReentrantLock cacheLock = new ReentrantLock();
  private final ConcurrentMap<ServiceDate, List<FixedLink>> fixedLinksByServiceDate = new ConcurrentHashMap<>();
  @Nullable
  private volatile Set<FixedLink> validFixedLinks = null;
  @Nullable
  private volatile Map<Stop, List<FixedLink>> fixedLinksByStop = null;

  // Pre-warms all lazy indices used by RaptorAlgorithmFactory.createFromDao so that
  // subsequent parallel calls across service dates are safe (reads on fully-built maps).
  // If OBA adds new lazy indices that createFromDao touches, add a warm-up call here.
  public void initialise() {
    getAllFixedLinks();
    initialiseFixedLinkByStopCacheIfNeeded();
    var trips = getAllTrips();
    if (!trips.isEmpty()) {
      var first = trips.iterator().next();
      getTripsForServiceId(first.getServiceId());
      getStopTimesForTrip(first);
    }
  }

  public Collection<FixedLink> getAllFixedLinks() {
    if (validFixedLinks == null) {
      cacheLock.lock();
      try {
        if (validFixedLinks == null) {
          LOGGER.info("Initialising fixed link cache...");

          var allFixedLinks = new HashSet<>(super.getAllEntitiesForType(FixedLink.class));

          var missingFromOrTo = allFixedLinks.stream()
                                             .filter(fixedLink -> fixedLink.getFromStop() == null || fixedLink.getToStop() == null)
                                             .collect(Collectors.toSet());

          missingFromOrTo.forEach(fixedLink -> LOGGER.warn("Missing from or to stop for fixed link: {}", fixedLink));

          validFixedLinks = Sets.difference(allFixedLinks, missingFromOrTo);

          LOGGER.info("Initialised fixed link cache. [Size: {}]", validFixedLinks.size());
        }
      } finally {
        cacheLock.unlock();
      }
    }

    return validFixedLinks;
  }

  public List<FixedLink> getFixedLinksFromStop(Stop stop) {
    initialiseFixedLinkByStopCacheIfNeeded();

    return fixedLinksByStop.getOrDefault(stop, Collections.emptyList());
  }

  public List<FixedLink> getFixedLinksRunningOnServiceDay(ServiceDate serviceDate) {
    return fixedLinksByServiceDate.computeIfAbsent(serviceDate, date -> getAllFixedLinks().stream()
                                                                                          .filter(fixedLink -> fixedLink.runsOn(serviceDate))
                                                                                          .sorted(Comparator.comparing(FixedLink::getStartDate))
                                                                                          .toList());
  }

  public Map<Stop, List<FixedLink>> getAllFixedLinksByStop() {
    initialiseFixedLinkByStopCacheIfNeeded();

    //TODO: defensive view... don't want it being tampered with.
    return fixedLinksByStop;
  }

  @Override
  public void clearAllCaches() {
    super.clearAllCaches();

    this.validFixedLinks = null;
    this.fixedLinksByStop = null;
    this.fixedLinksByServiceDate.clear();
  }

  private void initialiseFixedLinkByStopCacheIfNeeded() {
    if (fixedLinksByStop == null) {
      cacheLock.lock();
      try {
        if (fixedLinksByStop == null) {
          LOGGER.info("Initialising fixed link by stop cache...");
          fixedLinksByStop = getAllFixedLinks().stream().collect(Collectors.groupingBy(FixedLink::getFromStop));
          LOGGER.info("Initialised fixed link by stop cache.");
        }
      } finally {
        cacheLock.unlock();
      }
    }
  }
}
