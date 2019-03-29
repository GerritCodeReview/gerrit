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

package com.google.gerrit.server.index.group;

import static com.google.gerrit.index.SchemaUtil.schema;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;

/**
 * Definition of group index versions (schemata). See {@link SchemaDefinitions}.
 *
 * <p>Upgrades are subject to constraints, see {@code
 * com.google.gerrit.index.IndexUpgradeValidator}.
 */
public class GroupSchemaDefinitions extends SchemaDefinitions<InternalGroup> {
  @Deprecated
  static final Schema<InternalGroup> V5 =
      schema(
          /* version= */ 5,
          ImmutableList.of(),
          ImmutableList.of(
              GroupField.CREATED_ON_FIELD,
              GroupField.DESCRIPTION_FIELD,
              GroupField.ID_FIELD,
              GroupField.IS_VISIBLE_TO_ALL_FIELD,
              GroupField.MEMBER_FIELD,
              GroupField.NAME_FIELD,
              GroupField.NAME_PART_FIELD,
              GroupField.OWNER_UUID_FIELD,
              GroupField.REF_STATE_FIELD,
              GroupField.SUBGROUP_FIELD,
              GroupField.UUID_FIELD),
          ImmutableList.<IndexedField<InternalGroup, ?>.SearchSpec>of(
              GroupField.CREATED_ON_SPEC,
              GroupField.DESCRIPTION_SPEC,
              GroupField.ID_FIELD_SPEC,
              GroupField.IS_VISIBLE_TO_ALL_SPEC,
              GroupField.MEMBER_SPEC,
              GroupField.NAME_SPEC,
              GroupField.NAME_PART_SPEC,
              GroupField.OWNER_UUID_SPEC,
              GroupField.REF_STATE_SPEC,
              GroupField.SUBGROUP_SPEC,
              GroupField.UUID_FIELD_SPEC));

  // Bump Lucene version requires reindexing
  @Deprecated static final Schema<InternalGroup> V6 = schema(V5);

  // Lucene index was changed to add an additional field for sorting.
  @Deprecated static final Schema<InternalGroup> V7 = schema(V6);

  // New numeric types: use dimensional points using the k-d tree geo-spatial data structure
  // to offer fast single- and multi-dimensional numeric range.
  @Deprecated static final Schema<InternalGroup> V8 = schema(V7);

  // Upgrade Lucene to 7.x requires reindexing.
  @Deprecated static final Schema<InternalGroup> V9 = schema(V8);

  // Upgrade Lucene to 8.x requires reindexing.
  static final Schema<InternalGroup> V10 = schema(V9);

  /** Singleton instance of the schema definitions. This is one per JVM. */
  public static final GroupSchemaDefinitions INSTANCE = new GroupSchemaDefinitions();

  private GroupSchemaDefinitions() {
    super("groups", InternalGroup.class);
  }
}
