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

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaUtil;

/**
 * Index schema for projects.
 *
 * <p>Note that this class does not override {@link Object#equals(Object)}. It relies on instances
 * being singletons so that the default (i.e. reference) comparison works.
 */
public class ProjectField {
  private static byte[] toRefState(Project project) {
    return RefState.create(RefNames.REFS_CONFIG, project.getConfigRefState())
        .toByteArray(project.getNameKey());
  }

  public static final IndexedField<ProjectData, String> NAME_FIELD =
      IndexedField.<ProjectData>stringBuilder("RepoName")
          .required()
          .size(200)
          .stored()
          .build(p -> p.getProject().getName());

  public static final IndexedField<ProjectData, String>.SearchSpec NAME_SPEC =
      NAME_FIELD.exact("name");

  public static final IndexedField<ProjectData, String> DESCRIPTION_FIELD =
      IndexedField.<ProjectData>stringBuilder("Description")
          .stored()
          .build(p -> p.getProject().getDescription());

  public static final IndexedField<ProjectData, String>.SearchSpec DESCRIPTION_SPEC =
      DESCRIPTION_FIELD.fullText("description");

  public static final IndexedField<ProjectData, String> PARENT_NAME_FIELD =
      IndexedField.<ProjectData>stringBuilder("ParentName")
          .build(p -> p.getProject().getParentName());

  public static final IndexedField<ProjectData, String>.SearchSpec PARENT_NAME_SPEC =
      PARENT_NAME_FIELD.exact("parent_name");

  public static final IndexedField<ProjectData, Iterable<String>> NAME_PART_FIELD =
      IndexedField.<ProjectData>iterableStringBuilder("NamePart")
          .size(200)
          .build(p -> SchemaUtil.getNameParts(p.getProject().getName()));

  public static final IndexedField<ProjectData, Iterable<String>>.SearchSpec NAME_PART_SPEC =
      NAME_PART_FIELD.prefix("name_part");

  public static final IndexedField<ProjectData, String> STATE_FIELD =
      IndexedField.<ProjectData>stringBuilder("State")
          .stored()
          .build(p -> p.getProject().getState().name());

  public static final IndexedField<ProjectData, String>.SearchSpec STATE_SPEC =
      STATE_FIELD.exact("state");

  public static final IndexedField<ProjectData, Iterable<String>> ANCESTOR_NAME_FIELD =
      IndexedField.<ProjectData>iterableStringBuilder("AncestorName")
          .build(ProjectData::getParentNames);

  public static final IndexedField<ProjectData, Iterable<String>>.SearchSpec ANCESTOR_NAME_SPEC =
      ANCESTOR_NAME_FIELD.exact("ancestor_name");

  /**
   * All values of all refs that were used in the course of indexing this document. This covers
   * {@code refs/meta/config} of the current project and all of its parents.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final IndexedField<ProjectData, Iterable<byte[]>> REF_STATE_FIELD =
      IndexedField.<ProjectData>iterableByteArrayBuilder("RefState")
          .stored()
          .build(
              projectData ->
                  projectData.tree().stream()
                      .filter(p -> p.getProject().getConfigRefState() != null)
                      .map(p -> toRefState(p.getProject()))
                      .collect(toImmutableList()));

  public static final IndexedField<ProjectData, Iterable<byte[]>>.SearchSpec REF_STATE_SPEC =
      REF_STATE_FIELD.storedOnly("ref_state");
}
