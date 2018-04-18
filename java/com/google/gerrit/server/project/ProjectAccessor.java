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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

    ProjectAccessor create(ProjectConfig projectConfig);

    ProjectAccessor create(Project.NameKey projectName) throws NoSuchProjectException, IOException;

    ProjectAccessor createForAllProjects() throws NoSuchProjectException, IOException;
  }

  private final AllProjectsName allProjectsName;
  private final Factory factory;
  private final ProjectCache projectCache;
  private final ProjectState projectState;

  @VisibleForTesting // Please only use from RefControlTest.
  @AssistedInject
  public ProjectAccessor(
      Factory factory,
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      @Assisted ProjectState projectState) {
    this.factory = factory;
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.projectState = projectState;
  }

  @AssistedInject
  ProjectAccessor(
      Factory factory,
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      ProjectState.Factory projectStateFactory,
      @Assisted ProjectConfig projectConfig) {
    this(factory, projectCache, allProjectsName, projectStateFactory.create(projectConfig));
  }

  @AssistedInject
  ProjectAccessor(
      Factory factory,
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      @Assisted Project.NameKey projectName)
      throws NoSuchProjectException, IOException {
    this.factory = factory;
    this.projectCache = projectCache;
    this.allProjectsName = allProjectsName;
    this.projectState = projectCache.checkedGet(projectName);
    if (projectState == null) {
      // TODO(dborowitz): This doesn't include the stack trace from checkedGet, which was logged and
      // then discarded.
      throw new NoSuchProjectException(projectName);
    }
  }

  @AssistedInject
  ProjectAccessor(Factory factory, ProjectCache projectCache, AllProjectsName allProjectsName)
      throws NoSuchProjectException, IOException {
    this(factory, projectCache, allProjectsName, allProjectsName);
  }

  public Project getProject() {
    return getConfig().getProject();
  }

  public Project.NameKey getNameKey() {
    return getProject().getNameKey();
  }

  public String getName() {
    return getNameKey().get();
  }

  public ProjectConfig getConfig() {
    return projectState.getConfig();
  }

  public long getMaxObjectSizeLimit() {
    return getConfig().getMaxObjectSizeLimit();
  }

  public boolean statePermitsRead() {
    return getProject().getState().permitsRead();
  }

  public void checkStatePermitsRead() throws ResourceConflictException {
    if (!statePermitsRead()) {
      throw new ResourceConflictException(
          "project state " + getProject().getState().name() + " does not permit read");
    }
  }

  public boolean statePermitsWrite() {
    return getProject().getState().permitsWrite();
  }

  public void checkStatePermitsWrite() throws ResourceConflictException {
    if (!statePermitsWrite()) {
      throw new ResourceConflictException(
          "project state " + getProject().getState().name() + " does not permit write");
    }
  }

  // TODO(dborowitz): This is a convenience while migrating to ProjectAccessor, so that consumers
  // don't need to hold separate references to ProjectState/ProjectAccessor. Remove this method.
  public ProjectState getProjectState() {
    return projectState;
  }

  public SubmitType getSubmitType() {
    for (ProjectState s : tree()) {
      SubmitType t = s.getConfig().getProject().getConfiguredSubmitType();
      if (t != SubmitType.INHERIT) {
        return t;
      }
    }
    return Project.DEFAULT_ALL_PROJECTS_SUBMIT_TYPE;
  }

  public boolean is(BooleanProjectConfig config) {
    for (ProjectState s : tree()) {
      switch (s.getConfig().getProject().getBooleanConfig(config)) {
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

  /**
   * @return all {@link AccountGroup}'s to which the owner privilege for 'refs/*' is assigned for
   *     this project (the local owners), if there are no local owners the local owners of the
   *     nearest parent project that has local owners are returned
   */
  public Set<AccountGroup.UUID> getOwners() {
    for (ProjectState p : tree()) {
      if (!p.getLocalOwners().isEmpty()) {
        return p.getLocalOwners();
      }
    }
    return Collections.emptySet();
  }

  /**
   * @return all {@link AccountGroup}'s that are allowed to administrate the complete project. This
   *     includes all groups to which the owner privilege for 'refs/*' is assigned for this project
   *     (the local owners) and all groups to which the owner privilege for 'refs/*' is assigned for
   *     one of the parent projects (the inherited owners).
   */
  public Set<AccountGroup.UUID> getAllOwners() {
    Set<AccountGroup.UUID> result = new HashSet<>();

    for (ProjectState p : tree()) {
      result.addAll(p.getLocalOwners());
    }

    return result;
  }

  private Iterable<ProjectState> tree() {
    return () -> new ProjectHierarchyIterator(factory, projectCache, allProjectsName, projectState);
  }
}
