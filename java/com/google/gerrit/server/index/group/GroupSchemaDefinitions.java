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

  // New numeric types: use dimensional points using the k-d tree geo-spatial data structure
  // to offer fast single- and multi-dimensional numeric range.
  static final Schema<InternalGroup> V8 =
      schema(
          GroupField.CREATED_ON,
          GroupField.DESCRIPTION,
          GroupField.ID,
          GroupField.IS_VISIBLE_TO_ALL,
          GroupField.MEMBER,
          GroupField.NAME,
          GroupField.NAME_PART,
          GroupField.OWNER_UUID,
          GroupField.REF_STATE,
          GroupField.SUBGROUP,
          GroupField.UUID);

  public static final GroupSchemaDefinitions INSTANCE = new GroupSchemaDefinitions();

  private GroupSchemaDefinitions() {
    super("groups", InternalGroup.class);
  }
}
