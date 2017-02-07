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
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.util.TreeFormatter.TreeNode;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.SortedSet;
import java.util.TreeSet;

/** Node of a Project in a tree formatted by {@link ListProjects}. */
public class ProjectNode implements TreeNode, Comparable<ProjectNode> {
  public interface Factory {
    ProjectNode create(final Project project, final boolean isVisible);
  }

  private final AllProjectsName allProjectsName;
  private final Project project;
  private final boolean isVisible;

  private final SortedSet<ProjectNode> children = new TreeSet<>();

  @Inject
  protected ProjectNode(
      final AllProjectsName allProjectsName,
      @Assisted final Project project,
      @Assisted final boolean isVisible) {
    this.allProjectsName = allProjectsName;
    this.project = project;
    this.isVisible = isVisible;
  }

  /**
   * Returns the project parent name.
   *
   * @return Project parent name, {@code null} for the 'All-Projects' root project
   */
  public Project.NameKey getParentName() {
    return project.getParent(allProjectsName);
  }

  public boolean isAllProjects() {
    return allProjectsName.equals(project.getNameKey());
  }

  public Project getProject() {
    return project;
  }

  @Override
  public String getDisplayName() {
    return project.getName();
  }

  @Override
  public boolean isVisible() {
    return isVisible;
  }

  @Override
  public SortedSet<? extends ProjectNode> getChildren() {
    return children;
  }

  public void addChild(final ProjectNode child) {
    children.add(child);
  }

  @Override
  public int compareTo(final ProjectNode o) {
    return project.getNameKey().compareTo(o.project.getNameKey());
  }
}
