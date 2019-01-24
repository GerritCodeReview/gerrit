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

import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.server.group.InternalGroup;

public class GroupSchemaDefinitions extends SchemaDefinitions<InternalGroup> {
  @Deprecated
  static final Schema<InternalGroup> V2 =
      schema(
          GroupField.DESCRIPTION,
          GroupField.ID,
          GroupField.IS_VISIBLE_TO_ALL,
          GroupField.NAME,
          GroupField.NAME_PART,
          GroupField.OWNER_UUID,
          GroupField.UUID);

  @Deprecated static final Schema<InternalGroup> V3 = schema(V2, GroupField.CREATED_ON);

  @Deprecated
  static final Schema<InternalGroup> V4 = schema(V3, GroupField.MEMBER, GroupField.SUBGROUP);

  @Deprecated static final Schema<InternalGroup> V5 = schema(V4, GroupField.REF_STATE);

  // Bump Lucene version requires reindexing
  @Deprecated static final Schema<InternalGroup> V6 = schema(V5);

  // Lucene index was changed to add an additional field for sorting.
  @Deprecated static final Schema<InternalGroup> V7 = schema(V6);

  // Integer field type replaced with IntPoint and timestamp field type replaced with LongPoint
  static final Schema<InternalGroup> V8 = schema(V7);

  public static final GroupSchemaDefinitions INSTANCE = new GroupSchemaDefinitions();

  private GroupSchemaDefinitions() {
    super("groups", InternalGroup.class);
  }
}
