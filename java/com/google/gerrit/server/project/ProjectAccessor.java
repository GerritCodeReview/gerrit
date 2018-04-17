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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
  public interface Factory {
    ProjectAccessor create(ProjectState projectState);

    ProjectAccessor create(Project.NameKey projectName) throws NoSuchProjectException, IOException;
  }

  private final AllProjectsName allProjectsName;
  private final ProjectCache projectCache;
  private final ProjectState projectState;

  @VisibleForTesting // Please only use from RefControlTest.
  @AssistedInject
  public ProjectAccessor(
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      @Assisted ProjectState projectState) {
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.projectState = projectState;
  }

  @AssistedInject
  ProjectAccessor(
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      @Assisted Project.NameKey projectName)
      throws NoSuchProjectException, IOException {
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.projectState = projectCache.checkedGet(projectName);
    if (projectState == null) {
      // TODO(dborowitz): This doesn't include the stack trace from checkedGet, which was logged and
      // then discarded.
      throw new NoSuchProjectException(projectName);
    }
  }

  // TODO(dborowitz): This is a convenience while migrating to ProjectAccessor, so that consumers
  // don't need to hold separate references to ProjectState/ProjectAccessor. Remove this method.
  public ProjectState getProjectState() {
    return projectState;
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

  public boolean is(BooleanProjectConfig config) {
    for (ProjectState s : tree()) {
      switch (s.getProject().getBooleanConfig(config)) {
        case TRUE:
          return true;
        case FALSE:
          return false;
        case INHERIT:
        default:
          continue;
      }
    }
    return false;
  }

  /**
   * Obtain all local and inherited sections. This collection is looked up dynamically and is not
   * cached. Callers should try to cache this result per-request as much as possible.
   */
  public List<SectionMatcher> getAllSections() {
    List<SectionMatcher> all = new ArrayList<>();
    for (ProjectState s : tree()) {
      all.addAll(s.getLocalAccessSections());
    }
    return all;
  }

  public Set<GroupReference> getAllGroups() {
    return getGroups(getAllSections());
  }

  public Set<GroupReference> getLocalGroups() {
    return getGroups(projectState.getLocalAccessSections());
  }

  private static Set<GroupReference> getGroups(List<SectionMatcher> sectionMatcherList) {
    Set<GroupReference> all = new HashSet<>();
    for (SectionMatcher matcher : sectionMatcherList) {
      AccessSection section = matcher.getSection();
      for (Permission permission : section.getPermissions()) {
        for (PermissionRule rule : permission.getRules()) {
          all.add(rule.getGroup());
        }
      }
    }
    return all;
  }

  private Iterable<ProjectState> tree() {
    return () -> new ProjectHierarchyIterator(projectCache, allProjectsName, projectState);
  }
}
