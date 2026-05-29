// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.index.query;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.IndexedField.SearchSpec;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;

/** FieldBundle is an abstraction that allows retrieval of raw values from different sources. */
public class FieldBundle {

  // Map String => List{Integer, Long, Timestamp, String, byte[]}
  private ImmutableListMultimap<String, Object> fields;

  /**
   * Depending on the index implementation 1) either {@link IndexedField} are stored once and
   * referenced by {@link com.google.gerrit.index.IndexedField.SearchSpec} on the queries, 2) or
   * each {@link com.google.gerrit.index.IndexedField.SearchSpec} is stored individually.
   *
   * <p>In case #1 {@link #storesIndexedFields} is set to {@code true} and the {@link #fields}
   * contain a map from {@link IndexedField#name()} to a stored value.
   *
   * <p>In case #2 {@link #storesIndexedFields} is set to {@code false} and the {@link #fields}
   * contain a map from {@link SearchSpec#name()} to a stored value.
   */
  private final boolean storesIndexedFields;

  public FieldBundle(ListMultimap<String, Object> fields, boolean storesIndexedFields) {
    this.fields = ImmutableListMultimap.copyOf(fields);
    this.storesIndexedFields = storesIndexedFields;
  }

  /**
   * Get a field's value based on the field definition.
   *
   * @param schemaField the definition of the field of which the value should be retrieved. The
   *     field must be stored and contained in the result set as specified by {@link
   *     com.google.gerrit.index.QueryOptions}.
   * @param <T> Data type of the returned object based on the field definition
   * @return Either a single element or an Iterable based on the field definition. An empty list is
   *     returned for repeated fields that are not contained in the result.
   * @throws IllegalArgumentException if the requested field is not stored or not present. This
   *     check is only enforced on non-repeatable fields.
   */
  @SuppressWarnings("unchecked")
  public <T> T getValue(SchemaField<?, T> schemaField) {
    checkArgument(schemaField.isStored(), "Field must be stored");
    String storedFieldName =
        storesIndexedFields && schemaField instanceof IndexedField<?, ?>.SearchSpec
            ? ((IndexedField<?, ?>.SearchSpec) schemaField).getField().name()
            : schemaField.getName();
    checkArgument(
        fields.containsKey(storedFieldName) || schemaField.isRepeatable(),
        "Field %s is not in result set %s",
        storedFieldName,
        fields.keySet());

    ImmutableList<Object> result = fields.get(storedFieldName);
    if (schemaField.isRepeatable()) {
      return (T) result;
    }
    return (T) Iterables.getOnlyElement(result);
  }
}
