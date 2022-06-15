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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Specific version of a secondary index schema. */
public class Schema<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Builder<T> {
    private final Set<FieldDef<T, ?>> fields = new HashSet<>();
    private final Set<IndexedField<T, ?>.SearchSpec> searchSpecs = new HashSet<>();
    private final Set<IndexedField<T, ?>> indexedFields = new HashSet<>();

    private Optional<Integer> version = Optional.empty();

    public Builder<T> version(int version) {
      this.version = Optional.of(version);
      return this;
    }

    public Builder<T> add(Schema<T> schema) {
      this.indexedFields.addAll(schema.getIndexFields().values());
      this.searchSpecs.addAll(schema.getSearchSpecs().values());
      this.fields.addAll(schema.getFields().values());
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
      this.fields.addAll(fields);
      return this;
    }

    @SafeVarargs
    public final Builder<T> remove(FieldDef<T, ?>... fields) {
      this.fields.removeAll(Arrays.asList(fields));
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
      this.searchSpecs.addAll(searchSpecs);
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
      this.searchSpecs.removeAll(Arrays.asList(searchSpecs));
      return this;
    }

    @SafeVarargs
    public final Builder<T> remove(IndexedField<T, ?>... indexedFields) {
      for (IndexedField<T, ?> field : indexedFields) {
        ImmutableMap<String, ? extends IndexedField<T, ?>.SearchSpec> searchSpecs =
            field.getSearchSpecs();
        checkArgument(
            !searchSpecs.values().stream().anyMatch(this.searchSpecs::contains),
            "Field %s can be only removed from schema after all of it's searches are removed.",
            field.name());
      }
      this.indexedFields.removeAll(Arrays.asList(fields));
      return this;
    }

    public Schema<T> build() {
      checkState(version.isPresent());
      return new Schema<>(
          version.get(),
          ImmutableList.copyOf(fields),
          ImmutableList.copyOf(indexedFields),
          ImmutableList.copyOf(searchSpecs));
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

  private final ImmutableMap<String, FieldDef<T, ?>> fields;
  private final ImmutableSet<String> storedFields;

  private final ImmutableMap<String, IndexedField<T, ?>.SearchSpec> searchSpecs;
  private final ImmutableMap<String, IndexedField<T, ?>> indexedFields;

  private int version;

  private Schema(
      int version,
      Iterable<FieldDef<T, ?>> fields,
      Iterable<IndexedField<T, ?>> indexedFields,
      Iterable<IndexedField<T, ?>.SearchSpec> searchSpecs) {
    this.version = version;
    ImmutableSet.Builder<String> storedFieldsBuilder = ImmutableSet.builder();
    ImmutableMap.Builder<String, IndexedField<T, ?>.SearchSpec> fieldSpecBuilder =
        ImmutableMap.builder();
    ImmutableMap.Builder<String, IndexedField<T, ?>> indexFieldsBuilder = ImmutableMap.builder();
    for (IndexedField<T, ?> f : indexedFields) {
      indexFieldsBuilder.put(f.name(), f);
    }
    for (IndexedField<T, ?>.SearchSpec fieldSpec : searchSpecs) {
      if (fieldSpec.getField().stored()) {
        storedFieldsBuilder.add(fieldSpec.getName());
      }
      fieldSpecBuilder.put(fieldSpec.getName(), fieldSpec);
    }
    this.searchSpecs = fieldSpecBuilder.build();
    this.indexedFields = indexFieldsBuilder.build();

    ImmutableMap.Builder<String, FieldDef<T, ?>> b = ImmutableMap.builder();
    for (FieldDef<T, ?> f : fields) {
      checkArgument(
          !this.searchSpecs.containsKey(f.getName())
              && !this.indexedFields.containsKey(f.getName()),
          "Can not add %s FieldDef, because the field with this name already exists in schema",
          f.getName());
      b.put(f.getName(), f);
      if (f.isStored()) {
        storedFieldsBuilder.add(f.getName());
      }
    }
    this.fields = b.build();
    this.storedFields = storedFieldsBuilder.build();
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
  private final ImmutableMap<String, FieldDef<T, ?>> getFields() {
    return fields;
  }

  public final ImmutableMap<String, SchemaField<T, ?>> getSchemaFields() {
    HashMap<String, SchemaField<T, ?>> schemaFields = new HashMap<>();
    schemaFields.putAll(fields);
    schemaFields.putAll(searchSpecs);
    return ImmutableMap.copyOf(schemaFields);
  }

  public final ImmutableMap<String, IndexedField<T, ?>.SearchSpec> getSearchSpecs() {
    return searchSpecs;
  }

  public final ImmutableMap<String, IndexedField<T, ?>> getIndexFields() {
    return indexedFields;
  }

  /** Returns all fields in this schema where {@link FieldDef#isStored()} is true. */
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
    return getSchemaField(fieldName) != null;
  }

  private SchemaField<T, ?> getSchemaField(SchemaField<T, ?> field) {
    return getSchemaField(field.getName());
  }

  public SchemaField<T, ?> getSchemaField(String fieldName) {
    SchemaField<T, ?> f = searchSpecs.get(fieldName);
    if (f == null) {
      f = fields.get(fieldName);
    }
    return f;
  }

  private Values<T> fieldValues(T obj, SchemaField<T, ?> f, ImmutableSet<String> skipFields) {
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
      ImmutableList.Builder<Values<T>> valuesBuilder = ImmutableList.builder();
      HashSet<String> processedFields = new HashSet<>();
      for (IndexedField<T, ?>.SearchSpec fieldSpec : searchSpecs.values()) {
        Values<T> values = fieldValues(obj, fieldSpec, skipFields);
        if (values != null) {
          processedFields.add(fieldSpec.getName());
          valuesBuilder.add(values);
        }
      }
      for (FieldDef<T, ?> field : fields.values()) {
        if (processedFields.contains(field.getName())) {
          continue;
        }
        Values<T> values = fieldValues(obj, field, skipFields);
        if (values != null) {
          processedFields.add(field.getName());
          valuesBuilder.add(values);
        }
      }
      return valuesBuilder.build();

    } catch (StorageException e) {
      return ImmutableList.of();
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .addValue(fields.keySet())
        .addValue(indexedFields.keySet())
        .addValue(searchSpecs.keySet())
        .toString();
  }
}
