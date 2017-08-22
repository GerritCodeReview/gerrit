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

package com.google.gerrit.server.index.project;

import static com.google.gerrit.index.FieldDef.exact;
import static com.google.gerrit.index.FieldDef.fullText;
import static com.google.gerrit.index.FieldDef.prefix;

import com.google.common.collect.Iterables;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.server.project.ProjectState;

/** Index schema for projects. */
public class ProjectField {

  public static final FieldDef<ProjectState, String> NAME =
      exact("name").build(p -> p.getProject().getName());

  public static final FieldDef<ProjectState, String> DESCRIPTION =
      fullText("description").build(p -> p.getProject().getDescription());

  public static final FieldDef<ProjectState, String> PARENT_NAME =
      exact("parent_name").build(p -> p.getProject().getParentName());

  public static final FieldDef<ProjectState, Iterable<String>> NAME_PART =
      prefix("name_part").buildRepeatable(p -> SchemaUtil.getNameParts(p.getProject().getName()));

  public static final FieldDef<ProjectState, Iterable<String>> ANCESTOR_NAME =
      exact("ancestor_name")
          .buildRepeatable(
              p -> Iterables.transform(p.parents(), parent -> parent.getProject().getName()));
}
