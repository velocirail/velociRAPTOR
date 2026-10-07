package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.NullUnmarked;
import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.csv_entities.schema.annotations.CsvFields;
import org.onebusaway.gtfs.model.IdentityBean;

/**
 * A row of {@code attributions.txt}: who the feed credits for its data, and on what terms. OneBusAway has no model
 * for the file, so it is read here. Optional, as the file is: a feed without one has no attributions.
 * <p>
 * {@code attribution_licence} is not GTFS. gb-transit adds it to name the licence each source is published under
 * (the Rail Delivery Group's timetable under the Rail Settlement Plan data licence, say), and it is the part of the
 * row that says what may be done with the data, so it is kept.
 * <p>
 * The role flags are kept as text because the spec makes them optional and an empty one is not a {@code 0}.
 */
@SuppressWarnings("unused")
@NullUnmarked
@CsvFields(filename = "attributions.txt", required = false)
public class FeedAttribution extends IdentityBean<Integer> {
  @CsvField(ignore = true)
  private int id;

  @CsvField(name = "attribution_id", optional = true)
  private String attributionId;

  @CsvField(name = "organization_name")
  private String organizationName;

  @CsvField(name = "is_producer", optional = true)
  private String isProducer;

  @CsvField(name = "is_operator", optional = true)
  private String isOperator;

  @CsvField(name = "is_authority", optional = true)
  private String isAuthority;

  @CsvField(name = "attribution_url", optional = true)
  private String url;

  @CsvField(name = "attribution_email", optional = true)
  private String email;

  @CsvField(name = "attribution_phone", optional = true)
  private String phone;

  @CsvField(name = "attribution_licence", optional = true)
  private String licence;

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  public String getAttributionId() {
    return attributionId;
  }

  public void setAttributionId(String attributionId) {
    this.attributionId = attributionId;
  }

  public String getOrganizationName() {
    return organizationName;
  }

  public void setOrganizationName(String organizationName) {
    this.organizationName = organizationName;
  }

  public String getIsProducer() {
    return isProducer;
  }

  public void setIsProducer(String isProducer) {
    this.isProducer = isProducer;
  }

  public String getIsOperator() {
    return isOperator;
  }

  public void setIsOperator(String isOperator) {
    this.isOperator = isOperator;
  }

  public String getIsAuthority() {
    return isAuthority;
  }

  public void setIsAuthority(String isAuthority) {
    this.isAuthority = isAuthority;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getLicence() {
    return licence;
  }

  public void setLicence(String licence) {
    this.licence = licence;
  }

  /** Whether the row claims a role: its flag is {@code 1}. Empty and {@code 0} both mean it does not. */
  public static boolean flag(String value) {
    return "1".equals(value == null ? null : value.trim());
  }
}
