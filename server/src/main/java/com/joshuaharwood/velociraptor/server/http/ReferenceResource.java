package com.joshuaharwood.velociraptor.server.http;

import com.joshuaharwood.velociraptor.server.FeedReference;
import com.joshuaharwood.velociraptor.server.http.dto.ServerInfo;
import com.joshuaharwood.velociraptor.server.http.dto.Station;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/** What a client needs alongside the journeys: the stations they name, and which timetable they were planned on. */
@ApplicationScoped
@Path("/")
@Tag(name = "Reference data", description = "The loaded feed's stations, and which feed it is.")
@Produces(MediaType.APPLICATION_JSON)
public class ReferenceResource {
  private final FeedReference feedReference;

  @Inject
  public ReferenceResource(FeedReference feedReference) {
    this.feedReference = feedReference;
  }

  @GET
  @Path("stops")
  @Operation(summary = "Every station",
             description = "Each station a journey can start, end or change at, by the code the journey endpoints "
                           + "take and return, with its name, location, web page and step-free access. The list "
                           + "changes only when the server loads another feed, so it can be cached.")
  @APIResponse(responseCode = "200", description = "The stations, ordered by code.")
  public List<Station> stops() {
    return feedReference.stations();
  }

  @GET
  @Path("info")
  @Operation(summary = "The server and its feed",
             description = "The server's version, and which feed it is planning on: its format, version, the "
                           + "service dates it covers and who published it.")
  @APIResponse(responseCode = "200", description = "The server and its feed.")
  public ServerInfo info() {
    return feedReference.info();
  }
}
