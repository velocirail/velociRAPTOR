package com.joshuaharwood.velociraptor.server.http;

import com.joshuaharwood.velociraptor.server.GtfsDaoProducer;
import com.joshuaharwood.velociraptor.server.http.dto.Feed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@ApplicationScoped
@Path("/feed")
@Tag(name = "Feed", description = "The GTFS feed the server answers from, and where it came from.")
@Produces(MediaType.APPLICATION_JSON)
public class FeedResource {
    private final GtfsDaoProducer feeds;

    @Inject
    public FeedResource(GtfsDaoProducer feeds) {
        this.feeds = feeds;
    }

    @GET
    @Operation(summary = "The loaded feed",
            description = "Where the feed was read from and its digest, its `feed_info.txt`, and the sources its "
                    + "`attributions.txt` credits. Every response carries the feed's `id` in the `X-Feed-Id` header, "
                    + "so an answer can be matched to the feed that produced it.")
    @APIResponse(responseCode = "200", description = "The loaded feed.")
    public Feed feed() {
        return feeds.feed();
    }
}
