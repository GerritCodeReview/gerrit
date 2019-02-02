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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.index.FieldDef.exact;
import static com.google.gerrit.index.FieldDef.fullText;
import static com.google.gerrit.index.FieldDef.prefix;
import static com.google.gerrit.index.FieldDef.storedOnly;

import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;

/** Index schema for projects. */
public class ProjectField {
  private static byte[] toRefState(Project project) {
    return RefState.create(RefNames.REFS_CONFIG, project.getConfigRefState())
        .toByteArray(project.getNameKey());
  }

  public static final FieldDef<ProjectData, String> NAME =
      exact("name").sorted().stored().build(p -> p.getProject().getName());

  public static final FieldDef<ProjectData, String> DESCRIPTION =
      fullText("description").stored().build(p -> p.getProject().getDescription());

  public static final FieldDef<ProjectData, String> PARENT_NAME =
      exact("parent_name").build(p -> p.getProject().getParentName());

  public static final FieldDef<ProjectData, Iterable<String>> NAME_PART =
      prefix("name_part").buildRepeatable(p -> SchemaUtil.getNameParts(p.getProject().getName()));

  public static final FieldDef<ProjectData, String> STATE =
      exact("state").stored().build(p -> p.getProject().getState().name());

  public static final FieldDef<ProjectData, Iterable<String>> ANCESTOR_NAME =
      exact("ancestor_name").buildRepeatable(ProjectData::getParentNames);

  /**
   * All values of all refs that were used in the course of indexing this document. This covers
   * {@code refs/meta/config} of the current project and all of its parents.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final FieldDef<ProjectData, Iterable<byte[]>> REF_STATE =
      storedOnly("ref_state")
          .buildRepeatable(
              projectData ->
                  projectData
                      .tree()
                      .stream()
                      .filter(p -> p.getProject().getConfigRefState() != null)
                      .map(p -> toRefState(p.getProject()))
                      .collect(toImmutableList()));
}
