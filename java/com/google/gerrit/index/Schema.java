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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Specific version of a secondary index schema. */
public class Schema<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Builder<T> {
    private final List<FieldDef<T, ?>> fields = new ArrayList<>();
    private boolean useLegacyNumericFields;

    public Builder<T> add(Schema<T> schema) {
      this.fields.addAll(schema.getFields().values());
      return this;
    }

    @SafeVarargs
    public final Builder<T> add(FieldDef<T, ?>... fields) {
      this.fields.addAll(Arrays.asList(fields));
      return this;
    }

    @SafeVarargs
    public final Builder<T> remove(FieldDef<T, ?>... fields) {
      this.fields.removeAll(Arrays.asList(fields));
      return this;
    }

    public Builder<T> legacyNumericFields(boolean useLegacyNumericFields) {
      this.useLegacyNumericFields = useLegacyNumericFields;
      return this;
    }

    public Schema<T> build() {
      return new Schema<>(useLegacyNumericFields, ImmutableList.copyOf(fields));
    }
  }

  public static class Values<T> {
    private final FieldDef<T, ?> field;
    private final ImmutableList<?> values;

    private Values(FieldDef<T, ?> field, Stream<?> values) {
      this.field = field;
      this.values = values.collect(toImmutableList());
    }

    public FieldDef<T, ?> getField() {
      return field;
    }

    public Object getValue() {
      return values.get(0);
    }

    public ImmutableList<?> getValues() {
      return values;
    }
  }

  private static <T> FieldDef<T, ?> checkSame(FieldDef<T, ?> f1, FieldDef<T, ?> f2) {
    checkState(f1 == f2, "Mismatched %s fields: %s != %s", f1.getName(), f1, f2);
    return f1;
  }

  private final ImmutableMap<String, FieldDef<T, ?>> fields;
  private final ImmutableMap<String, FieldDef<T, ?>> storedFields;
  private final boolean useLegacyNumericFields;

  private int version;

  public Schema(boolean useLegacyNumericFields, Iterable<FieldDef<T, ?>> fields) {
    this(0, useLegacyNumericFields, fields);
  }

  public Schema(int version, boolean useLegacyNumericFields, Iterable<FieldDef<T, ?>> fields) {
    this.version = version;
    ImmutableMap.Builder<String, FieldDef<T, ?>> b = ImmutableMap.builder();
    ImmutableMap.Builder<String, FieldDef<T, ?>> sb = ImmutableMap.builder();
    for (FieldDef<T, ?> f : fields) {
      b.put(f.getName(), f);
      if (f.isStored()) {
        sb.put(f.getName(), f);
      }
    }
    this.fields = b.build();
    this.storedFields = sb.build();
    this.useLegacyNumericFields = useLegacyNumericFields;
  }

  public final int getVersion() {
    return version;
  }

  public final boolean useLegacyNumericFields() {
    return useLegacyNumericFields;
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
      return new Values<>(f, (Stream<?>) v);
    } else {
      return new Values<>(f, Stream.of(v));
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
  public final Stream<Values<T>> buildFields(T obj, ImmutableSet<String> skipFields) {
    try {
      return fields.values().stream()
          .map(f -> fieldValues(obj, f, skipFields))
          .filter(Objects::nonNull);
    } catch (StorageException e) {
      return Stream.empty();
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
