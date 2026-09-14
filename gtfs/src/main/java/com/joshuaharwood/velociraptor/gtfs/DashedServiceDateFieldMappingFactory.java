package com.joshuaharwood.velociraptor.gtfs;

import org.apache.commons.beanutils2.ConversionException;
import org.apache.commons.beanutils2.Converter;
import org.onebusaway.csv_entities.CsvEntityContext;
import org.onebusaway.csv_entities.exceptions.InvalidValueEntityException;
import org.onebusaway.csv_entities.schema.*;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.text.ParseException;
import java.util.Map;

/**
 * This is needed as when we write links.txt, we write their to and from dates with dashes between the date fields.
 */
public class DashedServiceDateFieldMappingFactory implements FieldMappingFactory {

  public FieldMapping createFieldMapping(EntitySchemaFactory schemaFactory, Class<?> entityType, String csvFieldName,
                                         String objFieldName, Class<?> objFieldType, boolean required) {
    return new FixedLinkServiceDateFieldMappingImpl(entityType, csvFieldName, objFieldName, required);
  }

  private static class FixedLinkServiceDateFieldMappingImpl extends AbstractFieldMapping implements Converter {

    public FixedLinkServiceDateFieldMappingImpl(Class<?> entityType, String csvFieldName, String objFieldName, boolean required) {
      super(entityType, csvFieldName, objFieldName, required);
    }

    public void translateFromCSVToObject(CsvEntityContext context, Map<String, Object> csvValues, BeanWrapper object) {

      if (isMissingAndOptional(csvValues)) {
        return;
      }

      Object value = csvValues.get(_csvFieldName);

      try {
        var withoutDashes = value.toString().replace("-", "");
        ServiceDate date = ServiceDate.parseString(withoutDashes);
        object.setPropertyValue(_objFieldName, date);
      } catch (ParseException ex) {
        throw new InvalidValueEntityException(_entityType, _csvFieldName, value.toString());
      }
    }

    public void translateFromObjectToCSV(CsvEntityContext context, BeanWrapper object, Map<String, Object> csvValues) {

      ServiceDate date = (ServiceDate) object.getPropertyValue(_objFieldName);
      // date fields can be optional -- return if not present
      if (date == null) {
        return;
      }
      String value = date.getAsString();

      var withDashes = "%s-%s-%s".formatted(value.substring(0, 4), value.substring(4, 6), value.substring(6, 8));
      csvValues.put(_csvFieldName, withDashes);
    }

    @Override
    public Object convert(@SuppressWarnings("rawtypes") Class type, Object value) {
      if (type == ServiceDate.class) {
        try {
          return ServiceDate.parseString(value.toString());
        } catch (ParseException ex) {
          throw new InvalidValueEntityException(_entityType, _csvFieldName, value.toString());
        }
      }
      throw new ConversionException("Could not convert " + value + " of type " + value.getClass() + " to " + type);
    }
  }
}
