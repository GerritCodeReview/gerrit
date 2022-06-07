// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.index;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.Field.FieldSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Specific version of a secondary index schema. */
public class Schema<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Builder<T> {

    private final List<Field<T, ?>.FieldSpec<T, ?>> fieldSpecs = new ArrayList<>();
    private final List<Field<T, ?>> indexFields = new ArrayList<>();

    public Builder<T> add(Schema<T> schema) {
      this.indexFields.addAll(schema.getIndexFields().values());
      this.fieldSpecs.addAll(schema.getFieldSpecs().values());

      return this;
    }


    @SafeVarargs
    public final Builder<T> add(Field<T, ?>... fields) {
      this.indexFields.addAll(Arrays.asList(fields));
      return this;
    }
    @SafeVarargs
    public final Builder<T> add(Field<T, ?>.FieldSpec<T, ?>... fields) {
      this.fieldSpecs.addAll(Arrays.asList(fields));
      return this;
    }

    // Only allow to remove FieldSpec, the or check if all field specs correspond to an existing field.
    @SafeVarargs
    public final Builder<T> remove(Field<T, ?>.FieldSpec<T, ?>... fields) {
      this.fieldSpecs.removeAll(Arrays.asList(fields));
      return this;
    }

    public Schema<T> build() {
      return new Schema<>(ImmutableList.copyOf(indexFields), ImmutableList.copyOf(fieldSpecs));
    }
  }

  public static class Values<T> {
    private final FieldDef<T, ?> field;
    private final Iterable<?> values;

    private Values(FieldDef<T, ?> field, Iterable<?> values) {
      this.field = field;
      this.values = values;
    }

    public FieldDef<T, ?> getField() {
      return field;
    }

    public Iterable<?> getValues() {
      return values;
    }
  }

  private static <T> FieldDef<T, ?> checkSame(FieldDef<T, ?> f1, FieldDef<T, ?> f2) {
    checkState(f1 == f2, "Mismatched %s fields: %s != %s", f1.getName(), f1, f2);
    return f1;
  }

  private final ImmutableMap<String, Field<T, ?>.FieldSpec<T, ?>> storedFields;

  private final ImmutableMap<String, Field<T, ?>.FieldSpec<T, ?>> fieldSpecs;
  private final ImmutableMap<String, Field<T, ?>> indexFields;

  private int version;

  public Schema( Iterable<Field<T, ?>> indexFields,
      Iterable<Field<T, ?>.FieldSpec<T, ?>> fieldSpecs) {
    this(0, indexFields, fieldSpecs);
  }

  public Schema(int version, Iterable<Field<T, ?>> indexFields,
      Iterable <Field<T, ?>.FieldSpec<T, ?>> fieldSpecs) {
    this.version = version;
    ImmutableMap.Builder<String, Field<T, ?>.FieldSpec<T, ?>> sb = ImmutableMap.builder();
    ImmutableMap.Builder<String, Field<T, ?>.FieldSpec<T, ?>> fieldSpecBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, Field<T, ?>> indexFieldsBuilder = ImmutableMap.builder();
    for (Field<T, ?> f : indexFields) {
      indexFieldsBuilder.put(f.name(), f);
    }
    for (Field<T, ?>.FieldSpec<T, ?> fieldSpec : fieldSpecs) {
      if (fieldSpec.getField().stored()) {
        sb.put(fieldSpec.getName(), fieldSpec);
      }
      fieldSpecBuilder.put(fieldSpec.getName(), fieldSpec);

    }
    this.fieldSpecs = fieldSpecBuilder.build();
    this.indexFields = indexFieldsBuilder.build();
  }

  public final int getVersion() {
    return version;
  }

  /**
   * Get all fields in this schema.
   *
   * <p>This is primarily useful for iteration. Most callers should prefer one of the helper methods
   * {@link #getField(FieldDef, FieldDef...)} or {@link #hasField(FieldDef)} to looking up fields by
   * name
   *
   * @return all fields in this schema indexed by name.
   */
  public final ImmutableMap<String, FieldDef<T, ?>> getFields() {
    return fields;
  }

  public final ImmutableMap<String, Field<T, ?>.FieldSpec<T, ?>> getFieldSpecs() {
    return fieldSpecs;
  }

  public final ImmutableMap<String, Field<T, ?>> getIndexFields() {
    return indexFields;
  }

  /** Returns all fields in this schema where {@link FieldDef#isStored()} is true. */
  public final ImmutableMap<String, FieldDef<T, ?>> getStoredFields() {
    return storedFields;
  }

  /**
   * Look up fields in this schema.
   *
   * @param first the preferred field to look up.
   * @param rest additional fields to look up.
   * @return the first field in the schema matching {@code first} or {@code rest}, in order, or
   *     absent if no field matches.
   */
  @SafeVarargs
  public final Optional<FieldDef<T, ?>> getField(FieldDef<T, ?> first, FieldDef<T, ?>... rest) {
    FieldDef<T, ?> field = fields.get(first.getName());
    if (field != null) {
      return Optional.of(checkSame(field, first));
    }
    for (FieldDef<T, ?> f : rest) {
      field = fields.get(f.getName());
      if (field != null) {
        return Optional.of(checkSame(field, f));
      }
    }
    return Optional.empty();
  }

  /**
   * Check whether a field is present in this schema.
   *
   * @param field field to look up.
   * @return whether the field is present.
   */
  public final boolean hasField(FieldDef<T, ?> field) {
    FieldDef<T, ?> f = fields.get(field.getName());
    if (f == null) {
      return false;
    }
    checkSame(f, field);
    return true;
  }

  private Values<T> fieldValues(T obj, FieldDef<T, ?> f, ImmutableSet<String> skipFields) {
    if (skipFields.contains(f.getName())) {
      return null;
    }

    Object v;
    try {
      v = f.get(obj);
    } catch (StorageException e) {
      // StorageException is thrown when the object is not found. On this case,
      // it is pointless to make further attempts for each field, so propagate
      // the exception to return an empty list.
      logger.atSevere().withCause(e).log("error getting field %s of %s", f.getName(), obj);
      throw e;
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("error getting field %s of %s", f.getName(), obj);
      return null;
    }
    if (v == null) {
      return null;
    } else if (f.isRepeatable()) {
      return new Values<>(f, (Iterable<?>) v);
    } else {
      return new Values<>(f, Collections.singleton(v));
    }
  }

  /**
   * Build all fields in the schema from an input object.
   *
   * <p>Null values are omitted, as are fields which cause errors, which are logged.
   *
   * @param obj input object.
   * @param skipFields set of field names to skip when indexing the document
   * @return all non-null field values from the object.
   */
  public final Iterable<Values<T>> buildFields(T obj, ImmutableSet<String> skipFields) {
    try {
      return fields.values().stream()
          .map(f -> fieldValues(obj, f, skipFields))
          .filter(Objects::nonNull)
          .collect(toImmutableList());
    } catch (StorageException e) {
      return ImmutableList.of();
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(fields.keySet()).toString();
  }

  public void setVersion(int version) {
    this.version = version;
  }
}
