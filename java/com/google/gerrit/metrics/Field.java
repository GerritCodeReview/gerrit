// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.metrics;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/**
 * Describes a bucketing field used by a metric.
 *
 * @param <T> type of field
 */
public class Field<T> {
  /**
   * Break down metrics by boolean true/false.
   *
   * @param name field name
   * @return boolean field
   */
  public static Field<Boolean> ofBoolean(String name) {
    return ofBoolean(name, null);
  }

  /**
   * Break down metrics by boolean true/false.
   *
   * @param name field name
   * @param description field description
   * @return boolean field
   */
  public static Field<Boolean> ofBoolean(String name, String description) {
    return new Field<>(name, Boolean.class, description);
  }

  /**
   * Break down metrics by cases of an enum.
   *
   * @param enumType type of enum
   * @param name field name
   * @return enum field
   */
  public static <E extends Enum<E>> Field<E> ofEnum(Class<E> enumType, String name) {
    return ofEnum(enumType, name, null);
  }

  /**
   * Break down metrics by cases of an enum.
   *
   * @param enumType type of enum
   * @param name field name
   * @param description field description
   * @return enum field
   */
  public static <E extends Enum<E>> Field<E> ofEnum(
      Class<E> enumType, String name, String description) {
    return new Field<>(name, enumType, description);
  }

  /**
   * Break down metrics by string.
   *
   * <p>Each unique string will allocate a new submetric. <b>Do not use user content as a field
   * value</b> as field values are never reclaimed.
   *
   * @param name field name
   * @return string field
   */
  public static Field<String> ofString(String name) {
    return ofString(name, null);
  }

  /**
   * Break down metrics by string.
   *
   * <p>Each unique string will allocate a new submetric. <b>Do not use user content as a field
   * value</b> as field values are never reclaimed.
   *
   * @param name field name
   * @param description field description
   * @return string field
   */
  public static Field<String> ofString(String name, String description) {
    return new Field<>(name, String.class, description);
  }

  /**
   * Break down metrics by integer.
   *
   * <p>Each unique integer will allocate a new submetric. <b>Do not use user content as a field
   * value</b> as field values are never reclaimed.
   *
   * @param name field name
   * @return integer field
   */
  public static Field<Integer> ofInteger(String name) {
    return ofInteger(name, null);
  }

  /**
   * Break down metrics by integer.
   *
   * <p>Each unique integer will allocate a new submetric. <b>Do not use user content as a field
   * value</b> as field values are never reclaimed.
   *
   * @param name field name
   * @param description field description
   * @return integer field
   */
  public static Field<Integer> ofInteger(String name, String description) {
    return new Field<>(name, Integer.class, description);
  }

  private final String name;
  private final Class<T> keyType;
  private final Function<T, String> formatter;
  private final String description;

  private Field(String name, Class<T> keyType, String description) {
    checkArgument(name.matches("^[a-z_]+$"), "name must match [a-z_]");
    this.name = name;
    this.keyType = keyType;
    this.formatter = initFormatter(keyType);
    this.description = description;
  }

  /** @return name of this field within the metric. */
  public String getName() {
    return name;
  }

  /** @return type of value used within the field. */
  public Class<T> getType() {
    return keyType;
  }

  /** @return description text for the field explaining its range of values. */
  public String getDescription() {
    return description;
  }

  public Function<T, String> formatter() {
    return formatter;
  }

  @SuppressWarnings("unchecked")
  private static <T> Function<T, String> initFormatter(Class<T> keyType) {
    if (keyType == String.class) {
      return (Function<T, String>) Functions.<String>identity();
    } else if (keyType == Integer.class || keyType == Boolean.class) {
      return (Function<T, String>) Functions.toStringFunction();
    } else if (Enum.class.isAssignableFrom(keyType)) {
      return in -> ((Enum<?>) in).name();
    }
    throw new IllegalStateException("unsupported type " + keyType.getName());
  }
}
