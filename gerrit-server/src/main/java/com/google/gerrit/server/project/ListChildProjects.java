// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class ListChildProjects implements RestReadView<ProjectResource> {

  @Option(name = "--recursive", usage = "to list child projects recursively")
  private boolean recursive;

  private final ProjectCache projectCache;
  private final AllProjectsName allProjects;
  private final ProjectJson json;
  private final ProjectNode.Factory projectNodeFactory;

  @Inject
  ListChildProjects(
      ProjectCache projectCache,
      AllProjectsName allProjectsName,
      ProjectJson json,
      ProjectNode.Factory projectNodeFactory) {
    this.projectCache = projectCache;
    this.allProjects = allProjectsName;
    this.json = json;
    this.projectNodeFactory = projectNodeFactory;
  }

  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
  }

  @Override
  public List<ProjectInfo> apply(ProjectResource rsrc) {
    if (recursive) {
      return getChildProjectsRecursively(rsrc.getNameKey(), rsrc.getControl().getUser());
    }
    return getDirectChildProjects(rsrc.getNameKey());
  }

  private List<ProjectInfo> getDirectChildProjects(Project.NameKey parent) {
    List<ProjectInfo> childProjects = new ArrayList<>();
    for (Project.NameKey projectName : projectCache.all()) {
      ProjectState e = projectCache.get(projectName);
      if (e == null) {
        // If we can't get it from the cache, pretend it's not present.
        continue;
      }
      if (parent.equals(e.getProject().getParent(allProjects))) {
        childProjects.add(json.format(e.getProject()));
      }
    }
    return childProjects;
  }

  private List<ProjectInfo> getChildProjectsRecursively(Project.NameKey parent, CurrentUser user) {
    Map<Project.NameKey, ProjectNode> projects = new HashMap<>();
    for (Project.NameKey name : projectCache.all()) {
      ProjectState p = projectCache.get(name);
      if (p == null) {
        // If we can't get it from the cache, pretend it's not present.
        continue;
      }
      projects.put(name, projectNodeFactory.create(p.getProject(), p.controlFor(user).isVisible()));
    }
    for (ProjectNode key : projects.values()) {
      ProjectNode node = projects.get(key.getParentName());
      if (node != null) {
        node.addChild(key);
      }
    }

    ProjectNode n = projects.get(parent);
    if (n != null) {
      return getChildProjectsRecursively(n);
    }
    return Collections.emptyList();
  }

  private List<ProjectInfo> getChildProjectsRecursively(ProjectNode p) {
    List<ProjectInfo> allChildren = new ArrayList<>();
    for (ProjectNode c : p.getChildren()) {
      if (c.isVisible()) {
        allChildren.add(json.format(c.getProject()));
        allChildren.addAll(getChildProjectsRecursively(c));
      }
    }
    return allChildren;
  }
}
