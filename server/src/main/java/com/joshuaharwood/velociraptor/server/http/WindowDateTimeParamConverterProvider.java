package com.joshuaharwood.velociraptor.server.http;

import io.quarkus.logging.Log;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

/**
 * Reads the {@code startDate}/{@code endDate} query parameters as instants.
 * <p>
 * The contract is ISO 8601 with a UTC offset ({@code 2026-09-08T08:45:00+01:00}, {@code 2026-09-08T07:45:00Z}),
 * the same form the responses use, so a client never has to know which zone the server thinks in. A zone-less
 * value ({@code 2026-09-08T08:45:00}) is still accepted and read as Europe/London wall-clock, exactly as the
 * previous {@code LocalDateTime} parameters were, so existing clients keep working; it is deprecated and logged
 * at WARN, and will be rejected once every client sends an offset. Anything else is a 400 naming the parameter,
 * rather than the empty 404 the default converter produced.
 */
@Provider
public class WindowDateTimeParamConverterProvider implements ParamConverterProvider {

  /** The zone GTFS times, service dates and zone-less input are all read in. */
  static final ZoneId LONDON = ZoneId.of("Europe/London");

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (rawType != OffsetDateTime.class) {
      return null;
    }
    String parameter = Arrays.stream(annotations)
                             .filter(QueryParam.class::isInstance)
                             .map(a -> ((QueryParam) a).value())
                             .findFirst()
                             .orElse("date-time");
    @SuppressWarnings("unchecked")
    ParamConverter<T> converter = (ParamConverter<T>) new WindowDateTimeConverter(parameter);
    return converter;
  }

  static final class WindowDateTimeConverter implements ParamConverter<OffsetDateTime> {

    private final String parameter;

    WindowDateTimeConverter(String parameter) {
      this.parameter = parameter;
    }

    @Override
    public OffsetDateTime fromString(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      try {
        return OffsetDateTime.parse(value);
      } catch (DateTimeParseException notAnOffsetDateTime) {
        // fall through to the deprecated zone-less form
      }
      try {
        OffsetDateTime resolved = LocalDateTime.parse(value).atZone(LONDON).toOffsetDateTime();
        Log.warnf("%s=%s carries no UTC offset and was read as Europe/London wall-clock (%s). Zone-less values are "
                  + "deprecated: send ISO 8601 with an offset.", parameter, value, resolved);
        return resolved;
      } catch (DateTimeParseException notALocalDateTime) {
        throw new BadRequestException(parameter + "=" + value + " is not an ISO 8601 date-time; expected an offset "
                                      + "form such as 2026-09-08T08:45:00+01:00");
      }
    }

    @Override
    public String toString(OffsetDateTime value) {
      return value == null ? null : value.toString();
    }
  }
}
