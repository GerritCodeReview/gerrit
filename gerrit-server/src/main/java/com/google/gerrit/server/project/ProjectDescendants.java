// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProjectDescendants {

  public interface Factory {
    ProjectDescendants create();
  }

  private final IdentifiedUser currentUser;
  private final ProjectCache projectCache;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final AllProjectsName allProjectsName;

  @Inject
  public ProjectDescendants(final IdentifiedUser currentUser,
      final ProjectCache projectCache,
      final ProjectControl.GenericFactory projectControlFactory,
      final AllProjectsName allProjectsName) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
    this.allProjectsName = allProjectsName;
  }

  /**
   * Returns all (visible) descendants of the given project.
   *
   * @param project name of the project for which all descendants should be
   *        returned
   * @return all (visible) descendants of the given project
   * @throws NoSuchProjectException if the given project does not exist or if it
   *         isn't visible to the current user
   */
  public Set<Project.NameKey> get(final Project.NameKey project)
      throws NoSuchProjectException {
    final ProjectControl pctl =
        projectControlFactory.controlFor(project, currentUser);
    if (!pctl.isVisible() && !pctl.isOwner()) {
      throw new NoSuchProjectException(project);
    }

    final ProjectNode projectNode = getProjectNode(project);
    return getDescendants(projectNode, new HashSet<Project.NameKey>());
  }

  private ProjectNode getProjectNode(final Project.NameKey project) {
    final Map<Project.NameKey, ProjectNode> projects =
        new HashMap<Project.NameKey, ProjectNode>();

    for (final Project.NameKey projectName : projectCache.all()) {
      final ProjectState e = projectCache.get(projectName);
      if (e == null) {
        // If we can't get it from the cache, pretend its not present.
        //
        continue;
      }

      final ProjectControl pctl = e.controlFor(currentUser);
      final boolean isVisible = pctl.isVisible() || pctl.isOwner();
      projects.put(projectName, new ProjectNode(pctl.getProject(), isVisible));
    }

    for (final ProjectNode projectNode : projects.values()) {
      final ProjectNode parentNode =
          projects.get(projectNode.getParentName());
      if (parentNode != null) {
        parentNode.children.add(projectNode);
      }
    }

    return projects.get(project);
  }

  private Set<Project.NameKey> getDescendants(final ProjectNode projectNode,
      final Set<Project.NameKey> seen) {
    seen.add(projectNode.project.getNameKey());
    final Set<Project.NameKey> descendants = new HashSet<Project.NameKey>();
    for (final ProjectNode child : projectNode.children) {
      if (child.isVisible) {
        descendants.add(child.project.getNameKey());
      }
      if (!seen.contains(child.project.getNameKey())) {
        descendants.addAll(getDescendants(child, seen));
      }
    }
    return descendants;
  }

  private class ProjectNode {
    final Project project;
    final Set<ProjectNode> children = new HashSet<ProjectNode>();
    final boolean isVisible;

    public ProjectNode(final Project project, final boolean isVisible) {
      this.project = project;
      this.isVisible = isVisible;
    }

    public Project.NameKey getParentName() {
      if (project.getParent() != null) {
        return project.getParent();
      }

      if (project.getNameKey().equals(allProjectsName)) {
        return null;
      }

      return allProjectsName;
    }
  }
}
