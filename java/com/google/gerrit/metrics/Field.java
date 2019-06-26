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
  @FunctionalInterface
  public interface MetadataMapper<T> {
    void map(Metadata.Builder metadataBuilder, T value);
  }

  public static Field.Builder<Boolean> ofBoolean(MetadataMapper<Boolean> metadataMapper) {
    return new AutoValue_Field.Builder<Boolean>()
        .valueType(Boolean.class)
        .metadataMapper(metadataMapper);
  }

  public static <E extends Enum<E>> Field.Builder<E> ofEnum(
      Class<E> enumType, MetadataMapper<E> metadataMapper) {
    return new AutoValue_Field.Builder<E>().valueType(enumType).metadataMapper(metadataMapper);
  }

  public static Field.Builder<Integer> ofInteger(MetadataMapper<Integer> metadataMapper) {
    return new AutoValue_Field.Builder<Integer>()
        .valueType(Integer.class)
        .metadataMapper(metadataMapper);
  }

  public static Field.Builder<String> ofString(MetadataMapper<String> metadataMapper) {
    return new AutoValue_Field.Builder<String>()
        .valueType(String.class)
        .metadataMapper(metadataMapper);
  }

  private Function<T, String> formatter;

  /** @return name of this field within the metric. */
  public abstract String name();

  /** @return type of value used within the field. */
  public abstract Class<T> valueType();

  public abstract MetadataMapper<T> metadataMapper();

  /** @return description text for the field explaining its range of values. */
  public abstract Optional<String> description();

  public Function<T, String> formatter() {
    if (formatter == null) {
      formatter = initFormatter(valueType());
    }
    return formatter;
  }

  private static <T> Function<T, String> initFormatter(Class<T> valueType) {
    if (valueType == String.class) {
      return s -> (String) s;
    } else if (valueType == Integer.class || valueType == Boolean.class) {
      return Object::toString;
    } else if (Enum.class.isAssignableFrom(valueType)) {
      return in -> ((Enum<?>) in).name();
    }
    throw new IllegalStateException("unsupported type " + valueType.getName());
  }

  @AutoValue.Builder
  public abstract static class Builder<T> {
    public abstract Builder<T> name(String name);

    abstract Builder<T> valueType(Class<T> type);

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
