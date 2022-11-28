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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Specific version of a secondary index schema. */
public class Schema<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Builder<T> {
    private final List<SchemaField<T, ?>> searchFields = new ArrayList<>();
    private final List<IndexedField<T, ?>> indexedFields = new ArrayList<>();

    private Optional<Integer> version = Optional.empty();

    public Builder<T> version(int version) {
      this.version = Optional.of(version);
      return this;
    }

    public Builder<T> add(Schema<T> schema) {
      this.indexedFields.addAll(schema.getIndexFields().values());
      this.searchFields.addAll(schema.getSchemaFields().values());
      if (!version.isPresent()) {
        version(schema.getVersion() + 1);
      }
      return this;
    }

    @SafeVarargs
    public final Builder<T> add(FieldDef<T, ?>... fields) {
      return add(ImmutableList.copyOf(fields));
    }

    public final Builder<T> add(ImmutableList<FieldDef<T, ?>> fields) {
      this.searchFields.addAll(fields);
      return this;
    }

    @SafeVarargs
    public final Builder<T> remove(FieldDef<T, ?>... fields) {
      this.searchFields.removeAll(Arrays.asList(fields));
      return this;
    }

    @SafeVarargs
    public final Builder<T> addSearchSpecs(IndexedField<T, ?>.SearchSpec... searchSpecs) {
      return addSearchSpecs(ImmutableList.copyOf(searchSpecs));
    }

    public Builder<T> addSearchSpecs(ImmutableList<IndexedField<T, ?>.SearchSpec> searchSpecs) {
      for (IndexedField<T, ?>.SearchSpec searchSpec : searchSpecs) {
        checkArgument(
            this.indexedFields.contains(searchSpec.getField()),
            "%s spec can only be added to the schema that contains %s field",
            searchSpec.getName(),
            searchSpec.getField().name());
      }
      this.searchFields.addAll(searchSpecs);
      return this;
    }

    @SafeVarargs
    public final Builder<T> addIndexedFields(IndexedField<T, ?>... fields) {
      return addIndexedFields(ImmutableList.copyOf(fields));
    }

    public Builder<T> addIndexedFields(ImmutableList<IndexedField<T, ?>> indexedFields) {
      this.indexedFields.addAll(indexedFields);
      return this;
    }

    @SafeVarargs
    public final Builder<T> remove(IndexedField<T, ?>.SearchSpec... searchSpecs) {
      this.searchFields.removeAll(Arrays.asList(searchSpecs));
      return this;
    }

    @SafeVarargs
    public final Builder<T> remove(IndexedField<T, ?>... indexedFields) {
      for (IndexedField<T, ?> field : indexedFields) {
        ImmutableMap<String, ? extends IndexedField<T, ?>.SearchSpec> searchSpecs =
            field.getSearchSpecs();
        checkArgument(
            !searchSpecs.values().stream().anyMatch(this.searchFields::contains),
            "Field %s can be only removed from schema after all of its searches are removed.",
            field.name());
      }
      this.indexedFields.removeAll(Arrays.asList(indexedFields));
      return this;
    }

    public Schema<T> build() {
      checkState(version.isPresent());
      return new Schema<>(
          version.get(), ImmutableList.copyOf(indexedFields), ImmutableList.copyOf(searchFields));
    }
  }

  public static class Values<T> {
    private final SchemaField<T, ?> field;
    private final Iterable<?> values;

    private Values(SchemaField<T, ?> field, Iterable<?> values) {
      this.field = field;
      this.values = values;
    }

    public SchemaField<T, ?> getField() {
      return field;
    }

    public Iterable<?> getValues() {
      return values;
    }
  }

  private static <T> SchemaField<T, ?> checkSame(SchemaField<T, ?> f1, SchemaField<T, ?> f2) {
    checkState(f1 == f2, "Mismatched %s fields: %s != %s", f1.getName(), f1, f2);
    return f1;
  }

  private final ImmutableSet<String> storedFields;

  private final ImmutableMap<String, SchemaField<T, ?>> schemaFields;
  private final ImmutableMap<String, IndexedField<T, ?>> indexedFields;

  private int version;

  private Schema(
      int version,
      ImmutableList<IndexedField<T, ?>> indexedFields,
      ImmutableList<SchemaField<T, ?>> schemaFields) {
    this.version = version;

    this.indexedFields =
        indexedFields.stream().collect(toImmutableMap(IndexedField::name, Function.identity()));
    this.schemaFields =
        schemaFields.stream().collect(toImmutableMap(SchemaField::getName, Function.identity()));

    Set<String> duplicateKeys =
        Sets.intersection(this.schemaFields.keySet(), this.indexedFields.keySet());
    checkArgument(
        duplicateKeys.isEmpty(),
        "DuplicateKeys found %s, indexFields:%s, schemaFields: %s",
        duplicateKeys,
        this.indexedFields.keySet(),
        this.schemaFields.keySet());
    this.storedFields =
        schemaFields.stream()
            .filter(SchemaField::isStored)
            .map(SchemaField::getName)
            .collect(toImmutableSet());
  }

  public final int getVersion() {
    return version;
  }

  /**
   * Get all fields in this schema.
   *
   * <p>This is primarily useful for iteration. Most callers should prefer one of the helper methods
   * {@link #getField(SchemaField, SchemaField...)} or {@link #hasField(SchemaField)} to looking up
   * fields by name
   *
   * @return all fields in this schema indexed by name.
   */
  public final ImmutableMap<String, SchemaField<T, ?>> getSchemaFields() {
    return schemaFields;
  }

  public final ImmutableMap<String, IndexedField<T, ?>> getIndexFields() {
    return indexedFields;
  }

  /**
   * Returns names of {@link SchemaField} fields in this schema where {@link SchemaField#isStored()}
   * is true.
   */
  public final ImmutableSet<String> getStoredFields() {
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
  public final Optional<SchemaField<T, ?>> getField(
      SchemaField<T, ?> first, SchemaField<T, ?>... rest) {
    SchemaField<T, ?> field = getSchemaField(first);
    if (field != null) {
      return Optional.of(checkSame(field, first));
    }
    for (SchemaField<T, ?> f : rest) {
      field = getSchemaField(first);
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
  public final boolean hasField(SchemaField<T, ?> field) {
    SchemaField<T, ?> f = getSchemaField(field);
    if (f == null) {
      return false;
    }
    checkSame(f, field);
    return true;
  }

  public final boolean hasField(String fieldName) {
    return this.getSchemaField(fieldName) != null;
  }

  private SchemaField<T, ?> getSchemaField(SchemaField<T, ?> field) {
    return getSchemaField(field.getName());
  }

  public SchemaField<T, ?> getSchemaField(String fieldName) {
    return schemaFields.get(fieldName);
  }

  private @Nullable Values<T> fieldValues(
      T obj, SchemaField<T, ?> f, ImmutableSet<String> skipFields) {
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
      return schemaFields.values().stream()
          .map(f -> fieldValues(obj, f, skipFields))
          .filter(Objects::nonNull)
          .collect(toImmutableList());

    } catch (StorageException e) {
      return ImmutableList.of();
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .addValue(indexedFields.keySet())
        .addValue(schemaFields.keySet())
        .toString();
  }
}
