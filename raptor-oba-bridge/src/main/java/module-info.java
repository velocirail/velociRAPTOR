import org.jspecify.annotations.NullMarked;

@NullMarked
module com.joshuaharwood.velociraptor.raptor.oba {
    requires com.joshuaharwood.velociraptor.raptor;
    requires com.joshuaharwood.velociraptor.gtfs;
    requires onebusaway.gtfs;
    requires org.jspecify;
    requires it.unimi.dsi.fastutil.core;

    exports com.joshuaharwood.velociraptor.obabridge;
}
