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
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CreateProjectHierarchy {

  public interface Factory {
    CreateProjectHierarchy create();
  }

  private final ProjectNode.Factory projectNodeFactory;
  private final IdentifiedUser currentUser;
  private final ProjectCache projectCache;

  private boolean all;

  private List<ProjectNode> sortedRootNodes;


  @Inject
  public CreateProjectHierarchy(final ProjectNode.Factory projectNodeFactory,
      final IdentifiedUser currentUser, final ProjectCache projectCache) {
    this.projectNodeFactory = projectNodeFactory;
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    createProjectHierarchies();
  }

  public void setAll(final boolean all) {
    this.all = all;
  }

  private void createProjectHierarchies() {
    final Map<NameKey, ProjectNode> projectHierarchies = new TreeMap<Project.NameKey, ProjectNode>();
    for (final Project.NameKey projectName : projectCache.all()) {
      final ProjectState e = projectCache.get(projectName);
      if (e == null) {
        // If we can't get it from the cache, pretend its not present.
        //
        continue;
      }

      final ProjectControl pctl = e.controlFor(currentUser);
      final boolean isVisible = pctl.isVisible() || (all && pctl.isOwner());
      projectHierarchies.put(projectName,
          projectNodeFactory.create(pctl.getProject(), isVisible));
    }

    sortedRootNodes = new ArrayList<ProjectNode>();
    for (final ProjectNode projectNode : projectHierarchies.values()) {
      if (projectNode.getParentName() == null) {
        sortedRootNodes.add(projectNode);
        continue;
      }

      final ProjectNode node =
          projectHierarchies.get(projectNode.getParentName());
      if (node != null) {
        node.addChild(projectNode);
      } else {
        // parent project does not exist -> add the project as root project
        sortedRootNodes.add(projectNode);
      }
    }
  }

  public List<ProjectNode> getRootProjectHierarchies() {
    return sortedRootNodes;
  }
}
