package com.joshuaharwood.velociraptor.server.http.errorhandling;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import io.quarkiverse.httpproblem.validation.HttpValidationProblem;
import io.quarkiverse.httpproblem.validation.Violation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gives every problem the library builds a {@code detail} an engineer can act on. Without this a validation problem
 * has none, a 405 repeats its title ({@code HTTP 405 Method Not Allowed}), a 404 and a 406 describe the framework
 * ({@code Unable to find matching target resource method}, {@code ...the value in @Produces}) rather than the API, and
 * a 500 has no detail at all.
 * <p>
 * The errors this API raises itself - a bad window, a stop in {@code notVia}, the server at capacity - are written
 * with their detail where they are thrown, and pass through unchanged.
 */
@ApplicationScoped
public class ReadableProblemDetails implements ProblemPostProcessor {

  static final String ENDPOINTS = "GET /, GET /detail and GET /first-arrival";

  @Override
  public HttpProblem apply(HttpProblem problem, ProblemContext context) {
    String detail = detailFor(problem, context.cause, context.path);
    if (detail == null) {
      return problem;
    }
    var builder = HttpProblem.builder(problem).withDetail(detail);
    if (problem instanceof HttpValidationProblem validation) {
      // The builder copies the standard members and the extra parameters, not a subclass's own fields.
      builder.with("violations", validation.getViolations());
    }
    return builder.build();
  }

  static @Nullable String detailFor(HttpProblem problem, @Nullable Throwable cause, @Nullable String path) {
    String at = path == null ? "this path" : path;
    var violations = violationsOf(problem);
    if (!violations.isEmpty()) {
      return violations.stream()
                       .sorted(Comparator.comparing((Violation v) -> v.field))
                       .map(v -> v.in + " parameter " + v.field + " " + v.message)
                       .collect(Collectors.joining("; ", "", "."));
    }
    return switch (cause) {
      case NotFoundException _ -> "There is no endpoint at " + at + ". The endpoints are " + ENDPOINTS + ".";
      case NotAllowedException _ -> at + " does not accept that method: every endpoint of this API accepts GET only.";
      case NotAcceptableException _ -> "Responses are application/json, which the request's Accept header does not allow.";
      case null, default -> problem.getStatusCode() >= 500 && problem.getDetail() == null
          ? "An unexpected error stopped the request. It has been logged, with the path and the time, on the server."
          : null;
    };
  }

  /**
   * A validation problem's violations. The library builds it as an {@link HttpValidationProblem}, but a post-processor
   * that runs before this one may have rebuilt it as a plain problem carrying them as its {@code violations} member.
   */
  private static List<Violation> violationsOf(HttpProblem problem) {
    if (problem instanceof HttpValidationProblem validation) {
      return validation.getViolations();
    }
    if (problem.getParameters().get("violations") instanceof List<?> list) {
      return list.stream().filter(Violation.class::isInstance).map(Violation.class::cast).toList();
    }
    return List.of();
  }
}
