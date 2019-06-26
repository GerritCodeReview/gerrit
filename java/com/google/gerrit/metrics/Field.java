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

import com.google.auto.value.AutoValue;
import com.google.gerrit.server.logging.Metadata;
import java.util.Optional;
import java.util.function.Function;

/**
 * Describes a bucketing field used by a metric.
 *
 * @param <T> type of field
 */
@AutoValue
public abstract class Field<T> {
  /** Maps a field value to a field in the {@link Metadata} class. */
  @FunctionalInterface
  public interface MetadataMapper<T> {
    void map(Metadata.Builder metadataBuilder, T value);
  }

  /**
   * Break down metrics by boolean true/false.
   *
   * @param name field name
   * @return builder for the boolean field
   */
  public static Field.Builder<Boolean> ofBoolean(
      String name, MetadataMapper<Boolean> metadataMapper) {
    return new AutoValue_Field.Builder<Boolean>()
        .valueType(Boolean.class)
        .formatter(Object::toString)
        .name(name)
        .metadataMapper(metadataMapper);
  }

  /**
   * Break down metrics by cases of an enum.
   *
   * @param enumType type of enum
   * @param name field name
   * @return builder for the enum field
   */
  public static <E extends Enum<E>> Field.Builder<E> ofEnum(
      Class<E> enumType, String name, MetadataMapper<E> metadataMapper) {
    return new AutoValue_Field.Builder<E>()
        .valueType(enumType)
        .formatter(Enum::name)
        .name(name)
        .metadataMapper(metadataMapper);
  }

  /**
   * Break down metrics by integer.
   *
   * <p>Each unique integer will allocate a new submetric. <b>Do not use user content as a field
   * value</b> as field values are never reclaimed.
   *
   * @param name field name
   * @return builder for the integer field
   */
  public static Field.Builder<Integer> ofInteger(
      String name, MetadataMapper<Integer> metadataMapper) {
    return new AutoValue_Field.Builder<Integer>()
        .valueType(Integer.class)
        .formatter(Object::toString)
        .name(name)
        .metadataMapper(metadataMapper);
  }

  /**
   * Break down metrics by string.
   *
   * <p>Each unique string will allocate a new submetric. <b>Do not use user content as a field
   * value</b> as field values are never reclaimed.
   *
   * @param name field name
   * @return builder for the string field
   */
  public static Field.Builder<String> ofString(String name, MetadataMapper<String> metadataMapper) {
    return new AutoValue_Field.Builder<String>()
        .valueType(String.class)
        .formatter(s -> s)
        .name(name)
        .metadataMapper(metadataMapper);
  }

  /** @return name of this field within the metric. */
  public abstract String name();

  /** @return type of value used within the field. */
  public abstract Class<T> valueType();

  public abstract MetadataMapper<T> metadataMapper();

  /** @return description text for the field explaining its range of values. */
  public abstract Optional<String> description();

  /** @return formatter to format field values. */
  public abstract Function<T, String> formatter();

  @AutoValue.Builder
  public abstract static class Builder<T> {
    abstract Builder<T> name(String name);

    abstract Builder<T> valueType(Class<T> type);

    abstract Builder<T> formatter(Function<T, String> formatter);

    abstract Builder<T> metadataMapper(MetadataMapper<T> metadataMapper);

    public abstract Builder<T> description(String description);

    abstract Field<T> autoBuild();

    public Field<T> build() {
      Field<T> field = autoBuild();
      checkArgument(field.name().matches("^[a-z_]+$"), "name must match [a-z_]");
      return field;
    }
  }
}
