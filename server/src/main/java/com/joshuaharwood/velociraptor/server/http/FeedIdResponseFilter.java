package com.joshuaharwood.velociraptor.server.http;

import com.joshuaharwood.velociraptor.server.GtfsDaoProducer;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Stamps every response with the id of the feed it was answered from, so a journey stored by a client can be traced
 * back to {@code GET /feed} - and through it to the exact file - after the server has moved on to a newer feed.
 * A header rather than a field, so no response body changes shape.
 */
@Provider
public class FeedIdResponseFilter implements ContainerResponseFilter {
    public static final String HEADER = "X-Feed-Id";

    private final GtfsDaoProducer feeds;

    @Inject
    public FeedIdResponseFilter(GtfsDaoProducer feeds) {
        this.feeds = feeds;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle(HEADER, feeds.feed().id());
    }
}
