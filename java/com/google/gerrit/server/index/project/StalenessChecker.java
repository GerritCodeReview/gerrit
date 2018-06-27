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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.project.ProjectCache;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

public class StalenessChecker {
  private static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(ProjectField.NAME.getName(), ProjectField.REF_STATE.getName());

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

  public boolean isStale(Project.NameKey project) throws IOException {
    ProjectData projectData = projectCache.get(project).toProjectData();
    ProjectIndex i = indexes.getSearchIndex();
    if (i == null) {
      return false; // No index; caller couldn't do anything if it is stale.
    }

    Optional<FieldBundle> result =
        i.getRaw(project, QueryOptions.create(indexConfig, 0, 1, FIELDS));
    if (!result.isPresent()) {
      return true;
    }

    return isStale(
        projectData, RefState.parseStates(result.get().getValue(ProjectField.REF_STATE)));
  }

  public static boolean isStale(
      ProjectData current, SetMultimap<Project.NameKey, RefState> storedRefStates) {
    if (storedRefStates.isEmpty()) {
      return true;
    }
    Iterable<ProjectData> projectAndParents =
        Iterables.concat(ImmutableList.of(current), current.getParents());
    for (ProjectData p : projectAndParents) {
      Set<RefState> storedStates = storedRefStates.get(p.getProject().getNameKey());
      if (storedStates.size() != 1) {
        return true; // We only store the config refs state for each project
      }
      RefState currentState =
          RefState.create(RefNames.REFS_CONFIG, p.getProject().getConfigRefState());
      if (!Iterables.getOnlyElement(storedStates).equals(currentState)) {
        // RefState doesn't match. The parent has seen a config update that we missed.
        return true;
      }
    }
    return false;
  }
}
