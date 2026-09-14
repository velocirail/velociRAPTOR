import org.jspecify.annotations.NullMarked;

@NullMarked
module com.joshuaharwood.velociraptor.raptor {
  requires org.jspecify;
  requires it.unimi.dsi.fastutil.core;

  exports com.joshuaharwood.velociraptor.raptor;
  exports com.joshuaharwood.velociraptor.raptor.model;
  exports com.joshuaharwood.velociraptor.raptor.query;
  exports com.joshuaharwood.velociraptor.raptor.result;
  exports com.joshuaharwood.velociraptor.raptor.result.filter;
}
