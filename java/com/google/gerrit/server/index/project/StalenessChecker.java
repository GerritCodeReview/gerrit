// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.server.index.StalenessCheckResult;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.util.Optional;

/**
 * Checker that compares values stored in the project index to metadata in NoteDb to detect index
 * documents that should have been updated (= stale).
 */
public class StalenessChecker {
  private static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(ProjectField.NAME_SPEC.getName(), ProjectField.REF_STATE_SPEC.getName());

  private final ProjectCache projectCache;
  private final ProjectIndexCollection indexes;
  private final IndexConfig indexConfig;

  @Inject
  StalenessChecker(
      ProjectCache projectCache, ProjectIndexCollection indexes, IndexConfig indexConfig) {
    this.projectCache = projectCache;
    this.indexes = indexes;
    this.indexConfig = indexConfig;
  }

  /**
   * Returns a {@link StalenessCheckResult} with structured information about staleness of the
   * provided {@link com.google.gerrit.entities.Project.NameKey}.
   */
  public StalenessCheckResult check(Project.NameKey project) {
    ProjectData projectData =
        projectCache.get(project).orElseThrow(illegalState(project)).toProjectData();
    ProjectIndex i = indexes.getSearchIndex();
    if (i == null) {
      return StalenessCheckResult
          .notStale(); // No index; caller couldn't do anything if it is stale.
    }

    Optional<FieldBundle> result =
        i.getRaw(project, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    if (!result.isPresent()) {
      return StalenessCheckResult.stale("Document %s missing from index", project);
    }

    SetMultimap<Project.NameKey, RefState> indexedRefStates =
        RefState.parseStates(result.get().getValue(ProjectField.REF_STATE_SPEC));

    SetMultimap<Project.NameKey, RefState> currentRefStates =
        MultimapBuilder.hashKeys().hashSetValues().build();
    projectData.tree().stream()
        .filter(p -> p.getProject().getConfigRefState() != null)
        .forEach(
            p ->
                currentRefStates.put(
                    p.getProject().getNameKey(),
                    RefState.create(RefNames.REFS_CONFIG, p.getProject().getConfigRefState())));

    if (currentRefStates.equals(indexedRefStates)) {
      return StalenessCheckResult.notStale();
    }
    return StalenessCheckResult.stale(
        "Document has unexpected ref states (%s != %s)", currentRefStates, indexedRefStates);
  }
}
