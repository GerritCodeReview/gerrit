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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;

/**
 * Accessor for read calls related to projects.
 *
 * <p>All calls which read configuration from a project's {@code refs/meta/config} are gathered
 * here. Other classes should always use this class in preference to the lower-level project
 * internals such as {@code ProjectCache} and {@code ProjectState}.
 *
 * <p>This class is a work in progress. Eventually, it will be the <em>only</em> place that contains
 * methods which walk the project inheritance hierarchy in order to read configuration.
 */
public class ProjectAccessor {
  @Singleton
  public static class Factory {
    private final ProjectCache projectCache;
    private final AssistedFactory factory;

    @Inject
    Factory(ProjectCache projectCache, AssistedFactory factory) {
      this.projectCache = projectCache;
      this.factory = factory;
    }

    public ProjectAccessor create(Project.NameKey projectName) throws IOException {
      return create(projectCache.checkedGet(projectName));
    }

    // TODO(dborowitz): Replace with a factory method that takes ProjectCacheEntry.
    public ProjectAccessor create(ProjectState projectState) {
      return factory.create(projectState);
    }
  }

  interface AssistedFactory {
    ProjectAccessor create(ProjectState projectState);
  }

  private final AllProjectsName allProjectsName;
  private final ProjectCache projectCache;
  private final ProjectState projectState;

  @Inject
  ProjectAccessor(
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      @Assisted ProjectState projectState) {
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.projectState = projectState;
  }

  public SubmitType getSubmitType() {
    for (ProjectState s : tree()) {
      SubmitType t = s.getProject().getConfiguredSubmitType();
      if (t != SubmitType.INHERIT) {
        return t;
      }
    }
    return Project.DEFAULT_ALL_PROJECTS_SUBMIT_TYPE;
  }

  private Iterable<ProjectState> tree() {
    return () -> new ProjectHierarchyIterator(projectCache, allProjectsName, projectState);
  }
}
