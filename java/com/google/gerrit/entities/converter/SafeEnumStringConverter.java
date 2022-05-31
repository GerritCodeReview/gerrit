package com.google.gerrit.entities.converter;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.collect.Iterables;
import java.util.EnumSet;
import java.util.Set;

/**
 * Converts a string to an enum value with the same name. If the string is not the name of any enum
 * constant in the specified enum, then the first value is returned as a fallback.
 */
public class SafeEnumStringConverter<T extends Enum<T>> {
  private Converter<String, T> enumConverter;
  private Set<T> values;

  public SafeEnumStringConverter(Class<T> t) {
    this.enumConverter = Enums.stringConverter(t);
    this.values = EnumSet.allOf(t);
  }

  public T convert(String value) {
    try {
      return enumConverter.convert(value);
    } catch (IllegalArgumentException e) {
      return Iterables.getFirst(values, null);
    }
  }

  public String reverseConvert(T t) {
    return enumConverter.reverse().convert(t);
  }
}
