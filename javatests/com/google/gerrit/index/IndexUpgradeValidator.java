// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import org.junit.Ignore;

/**
 * Validates index upgrades to enforce the following constraints: Upgrades may only add or remove
 * fields. They may not do both, and may not change field types.
 */
@Ignore
public class IndexUpgradeValidator {

  public static void assertValid(Schema<?> previousSchema, Schema<?> schema) {
    assertValid(previousSchema.getSchemaFields(), schema.getSchemaFields(), schema.getVersion());
    assertValid(previousSchema.getIndexFields(), schema.getIndexFields(), schema.getVersion());
  }

  private static void assertValid(
      ImmutableMap<String, ?> previousSchemaFields,
      ImmutableMap<String, ?> schemaFields,
      int schemaVersion) {
    SetView<String> addedFields =
        Sets.difference(schemaFields.keySet(), previousSchemaFields.keySet());
    SetView<String> removedFields =
        Sets.difference(previousSchemaFields.keySet(), schemaFields.keySet());
    SetView<String> keptFields =
        Sets.intersection(previousSchemaFields.keySet(), schemaFields.keySet());
    assertWithMessage(
            "Schema upgrade to version "
                + schemaVersion
                + " may either add or remove fields, but not both")
        .that(addedFields.isEmpty() || removedFields.isEmpty())
        .isTrue();
    ImmutableList<String> modifiedFields =
        keptFields.stream()
            .filter(fieldName -> previousSchemaFields.get(fieldName) != schemaFields.get(fieldName))
            .collect(toImmutableList());
    assertWithMessage("Fields may not be modified (create a new field instead)")
        .that(modifiedFields)
        .isEmpty();
  }

  private IndexUpgradeValidator() {}
}
