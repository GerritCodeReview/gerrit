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

package com.google.gerrit.index.project;

import static com.google.gerrit.index.SchemaUtil.schema;

import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaDefinitions;

public class ProjectSchemaDefinitions extends SchemaDefinitions<ProjectData> {

  @Deprecated
  static final Schema<ProjectData> V1 =
      schema(
          ProjectField.NAME,
          ProjectField.DESCRIPTION,
          ProjectField.PARENT_NAME,
          ProjectField.NAME_PART,
          ProjectField.ANCESTOR_NAME);

  @Deprecated
  static final Schema<ProjectData> V2 = schema(V1, ProjectField.STATE, ProjectField.REF_STATE);

  // Bump Lucene version requires reindexing
  @Deprecated static final Schema<ProjectData> V3 = schema(V2);

  // Lucene index was changed to add an additional field for sorting.
  @Deprecated static final Schema<ProjectData> V4 = schema(V3);

  // Remove "_SORT" suffix from NAME_SORT field name
  static final Schema<ProjectData> V5 = schema(V4);

  public static final ProjectSchemaDefinitions INSTANCE = new ProjectSchemaDefinitions();

  public static final String NAME = "projects";

  private ProjectSchemaDefinitions() {
    super(NAME, ProjectData.class);
  }
}
