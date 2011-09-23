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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.List;

public class ProjectNode {

  public interface Factory {
    ProjectNode create(Project project, boolean isVisible);
  }

  private final AllProjectsName allProjectsName;

  private final Project project;
  private final boolean isVisible;
  private final List<ProjectNode> children;

  @Inject
  ProjectNode(final AllProjectsName allProjectsName,
      @Assisted final Project project, @Assisted final boolean isVisible) {
    this.allProjectsName = allProjectsName;
    this.project = project;
    this.isVisible = isVisible;
    this.children = new ArrayList<ProjectNode>();
  }

  /**
   * Returns the project.
   *
   * @return the project
   */
  public Project getProject() {
    return project;
  }

  /**
   * Returns the project parent name.
   *
   * @return Project parent name, <code>null</code> for the 'All-Projects' root
   *         project
   */
  public Project.NameKey getParentName() {
    if (project.getParent() != null) {
      return project.getParent();
    }

    if (project.getNameKey().equals(allProjectsName)) {
      return null;
    }

    return allProjectsName;
  }

  /**
   * Checks if the project is visible to the user.
   *
   * @return <code>true</code> if the project is visible, otherwise
   *         <code>false</code>
   */
  public boolean isVisible() {
    return isVisible;
  }

  /**
   * Returns the list of children nodes.
   *
   * @return List of children nodes
   */
  public List<ProjectNode> getChildren() {
    return children;
  }

  /**
   * Adds a child to this project node.
   *
   * @param childProject child project that should be added to this project node
   */
  public void addChild(final ProjectNode childProject) {
    children.add(childProject);
  }

  /**
   * Adds children to this project node.
   *
   * @param childProjects child projects that should be added to this project
   *        node
   */
  public void addChildren(final List<ProjectNode> childProjects) {
    children.addAll(childProjects);
  }

  /**
   * Checks if the project is a leaf.
   *
   * @return <code>true</code> if the projects is a leaf, otherwise
   *         <code>false</code>
   */
  public boolean isLeaf() {
    return children.size() == 0;
  }
}
