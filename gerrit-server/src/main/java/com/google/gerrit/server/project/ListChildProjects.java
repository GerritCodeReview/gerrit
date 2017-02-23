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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class ListChildProjects implements RestReadView<ProjectResource> {

  @Option(name = "--recursive", usage = "to list child projects recursively")
  private boolean recursive;

  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final AllProjectsName allProjects;
  private final ProjectJson json;

  @Inject
  ListChildProjects(
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      AllProjectsName allProjectsName,
      ProjectJson json) {
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.allProjects = allProjectsName;
    this.json = json;
  }

  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
  }

  @Override
  public List<ProjectInfo> apply(ProjectResource rsrc) throws PermissionBackendException {
    if (recursive) {
      return recursiveChildProjects(rsrc.getNameKey());
    }
    return directChildProjects(rsrc.getNameKey());
  }

  private List<ProjectInfo> directChildProjects(Project.NameKey parent)
      throws PermissionBackendException {
    Map<Project.NameKey, Project> children = new HashMap<>();
    for (Project.NameKey name : projectCache.all()) {
      ProjectState c = projectCache.get(name);
      if (c != null && parent.equals(c.getProject().getParent(allProjects))) {
        children.put(c.getProject().getNameKey(), c.getProject());
      }
    }
    return permissionBackend
        .user(user)
        .filter(ProjectPermission.ACCESS, children.keySet())
        .stream()
        .sorted((a, b) -> a.get().compareTo(b.get()))
        .map((p) -> json.format(children.get(p)))
        .collect(toList());
  }

  private List<ProjectInfo> recursiveChildProjects(Project.NameKey parent)
      throws PermissionBackendException {
    Map<Project.NameKey, Project> projects = readAllProjects();
    Multimap<Project.NameKey, Project.NameKey> children = parentToChildren(projects);
    PermissionBackend.WithUser perm = permissionBackend.user(user);

    List<ProjectInfo> results = new ArrayList<>();
    depthFirstFormat(results, perm, projects, children, parent);
    return results;
  }

  private Map<Project.NameKey, Project> readAllProjects() {
    Map<Project.NameKey, Project> projects = new HashMap<>();
    for (Project.NameKey name : projectCache.all()) {
      ProjectState c = projectCache.get(name);
      if (c != null) {
        projects.put(c.getProject().getNameKey(), c.getProject());
      }
    }
    return projects;
  }

  /** Map of parent project to direct child. */
  private Multimap<Project.NameKey, Project.NameKey> parentToChildren(
      Map<Project.NameKey, Project> projects) {
    Multimap<Project.NameKey, Project.NameKey> m = ArrayListMultimap.create();
    for (Map.Entry<Project.NameKey, Project> e : projects.entrySet()) {
      m.put(e.getValue().getParent(allProjects), e.getKey());
    }
    return m;
  }

  private void depthFirstFormat(
      List<ProjectInfo> results,
      PermissionBackend.WithUser perm,
      Map<Project.NameKey, Project> projects,
      Multimap<Project.NameKey, Project.NameKey> children,
      Project.NameKey parent)
      throws PermissionBackendException {
    List<Project.NameKey> canSee =
        perm.filter(ProjectPermission.ACCESS, children.get(parent))
            .stream()
            .sorted((a, b) -> a.get().compareTo(b.get()))
            .collect(toList());
    children.removeAll(parent); // removing all entries prevents cycles.

    for (Project.NameKey c : canSee) {
      results.add(json.format(projects.get(c)));
      depthFirstFormat(results, perm, projects, children, c);
    }
  }
}
