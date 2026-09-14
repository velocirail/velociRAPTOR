import org.jspecify.annotations.NullMarked;

@NullMarked
module com.joshuaharwood.velociraptor.gtfs {
  requires onebusaway.csv.entities;
  requires onebusaway.gtfs;
  requires org.apache.commons.beanutils2;
  requires org.slf4j;
  requires org.jspecify;
  requires com.google.common;
  exports com.joshuaharwood.velociraptor.gtfs;
}