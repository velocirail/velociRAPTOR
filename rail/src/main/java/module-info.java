import org.jspecify.annotations.NullMarked;

@NullMarked
module com.joshuaharwood.velociraptor.rail {
  requires com.joshuaharwood.velociraptor.raptor;
  requires org.jspecify;

  exports com.joshuaharwood.velociraptor.rail;
}
