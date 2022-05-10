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
import com.google.common.collect.ListMultimap;
import com.google.gerrit.index.FieldDef;

/** FieldBundle is an abstraction that allows retrieval of raw values from different sources. */
public class FieldBundle {

  // Map String => List{Integer, Long, Timestamp, String, byte[]}
  private ImmutableListMultimap<String, Object> fields;

  public FieldBundle(ListMultimap<String, Object> fields) {
    this.fields = ImmutableListMultimap.copyOf(fields);
  }

  /**
   * Get a field's value based on the field definition.
   *
   * @param fieldDef the definition of the field of which the value should be retrieved. The field
   *     must be stored and contained in the result set as specified by {@link
   *     com.google.gerrit.index.QueryOptions}.
   * @param <T> Data type of the returned object based on the field definition
   * @return Either a single element or an Iterable based on the field definition. An empty list is
   *     returned for repeated fields that are not contained in the result.
   * @throws IllegalArgumentException if the requested field is not stored or not present. This
   *     check is only enforced on non-repeatable fields.
   */
  @SuppressWarnings("unchecked")
  public <T> T getValue(FieldDef<?, T> fieldDef) {
    checkArgument(fieldDef.isStored(), "Field must be stored");
    checkArgument(
        fields.containsKey(fieldDef.getName()) || fieldDef.isRepeatable(),
        "Field %s is not in result set %s",
        fieldDef.getName(),
        fields.keySet());

    ImmutableList<Object> result = fields.get(fieldDef.getName());
    if (fieldDef.isRepeatable()) {
      return (T) result.stream();
    }
    return (T) result.get(0);
  }
}
