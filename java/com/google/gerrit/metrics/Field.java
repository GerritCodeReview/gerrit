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
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.gerrit.server.logging.Metadata;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes a bucketing field used by a metric.
 *
 * @param <T> type of field
 * @param name Returns name of this field within the metric.
 * @param valueType Returns type of value used within the field.
 * @param metadataMapper Returns mapper that maps a field value to a field in the {@link Metadata}
 *     class.
 * @param description Returns description text for the field explaining its range of values.
 * @param formatter Returns formatter to format field values.
 */
public record Field<T>(
    String name,
    Class<T> valueType,
    BiConsumer<Metadata.Builder, T> metadataMapper,
    Optional<String> description,
    Function<T, String> formatter) {
  public Field {
    requireNonNull(name, "name");
    requireNonNull(valueType, "valueType");
    requireNonNull(metadataMapper, "metadataMapper");
    requireNonNull(description, "description");
    requireNonNull(formatter, "formatter");
  }

  public static <T> BiConsumer<Metadata.Builder, T> ignoreMetadata() {
    return (metadataBuilder, fieldValue) -> {};
  }

  /**
   * Break down metrics by boolean true/false.
   *
   * @param name field name
   * @return builder for the boolean field
   */
  public static Field.Builder<Boolean> ofBoolean(
      String name, BiConsumer<Metadata.Builder, Boolean> metadataMapper) {
    return new AutoBuilder_Field_Builder<Boolean>()
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
      Class<E> enumType, String name, BiConsumer<Metadata.Builder, String> metadataMapper) {
    return new AutoBuilder_Field_Builder<E>()
        .valueType(enumType)
        .formatter(Enum::name)
        .name(name)
        .metadataMapper(
            (metadataBuilder, fieldValue) ->
                metadataMapper.accept(metadataBuilder, fieldValue.name()));
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
      String name, BiConsumer<Metadata.Builder, Integer> metadataMapper) {
    return new AutoBuilder_Field_Builder<Integer>()
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
  public static Field.Builder<String> ofString(
      String name, BiConsumer<Metadata.Builder, String> metadataMapper) {
    return new AutoBuilder_Field_Builder<String>()
        .valueType(String.class)
        .formatter(s -> s)
        .name(name)
        .metadataMapper(metadataMapper);
  }

  /**
   * A dedicated field to be used with metrics based on {@link Metadata#projectName()}. It was
   * introduced to sanitize the project name to avoid sub-metric name's collision.
   *
   * @param fieldName name of the field that contains a project name as value
   * @return builder for the project name field
   */
  public static Field.Builder<String> ofProjectName(String fieldName) {
    return new AutoBuilder_Field_Builder<String>()
        .valueType(String.class)
        .formatter(Field::sanitizeProjectName)
        .name(fieldName)
        .metadataMapper(Metadata.Builder::projectName);
  }

  @AutoBuilder
  public abstract static class Builder<T> {
    abstract Builder<T> name(String name);

    abstract Builder<T> valueType(Class<T> type);

    abstract Builder<T> formatter(Function<T, String> formatter);

    abstract Builder<T> metadataMapper(BiConsumer<Metadata.Builder, T> metadataMapper);

    public abstract Builder<T> description(String description);

    abstract Field<T> autoBuild();

    public Field<T> build() {
      Field<T> field = autoBuild();
      checkArgument(field.name().matches("^[a-z_]+$"), "name must match [a-z_]");
      return field;
    }
  }

  private static final Pattern SUBMETRIC_NAME_PATTERN =
      Pattern.compile("[a-zA-Z0-9_-]+([a-zA-Z0-9_-]+)*");
  private static final Pattern INVALID_CHAR_PATTERN = Pattern.compile("[^\\w-]");
  private static final String REPLACEMENT_PREFIX = "_0x";

  private static String sanitizeProjectName(String projectName) {
    if (SUBMETRIC_NAME_PATTERN.matcher(projectName).matches()
        && !projectName.contains(REPLACEMENT_PREFIX)) {
      return projectName;
    }

    String replacmentPrefixSanitizedName =
        projectName.replaceAll(REPLACEMENT_PREFIX, REPLACEMENT_PREFIX + REPLACEMENT_PREFIX);
    StringBuilder sanitizedName = new StringBuilder();
    for (int i = 0; i < replacmentPrefixSanitizedName.length(); i++) {
      Character c = replacmentPrefixSanitizedName.charAt(i);
      Matcher matcher = INVALID_CHAR_PATTERN.matcher(c.toString());
      if (matcher.matches()) {
        sanitizedName.append(REPLACEMENT_PREFIX);
        sanitizedName.append(Integer.toHexString(c).toUpperCase());
        sanitizedName.append('_');
      } else {
        sanitizedName.append(c);
      }
    }

    return sanitizedName.toString();
  }
}
