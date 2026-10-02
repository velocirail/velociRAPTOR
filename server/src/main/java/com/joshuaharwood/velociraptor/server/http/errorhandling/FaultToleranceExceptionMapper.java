package com.joshuaharwood.velociraptor.server.http.errorhandling;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;

import java.util.Map;

@ApplicationScoped
public class FaultToleranceExceptionMapper implements ExceptionMapper<FaultToleranceException> {
  @Override
  public Response toResponse(FaultToleranceException exception) {
    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                   .type(MediaType.APPLICATION_JSON)
                   .header(HttpHeaders.RETRY_AFTER, "10")
                   .entity(Map.of("error", "Server at capacity, please try again later"))
                   .build();
  }
}
