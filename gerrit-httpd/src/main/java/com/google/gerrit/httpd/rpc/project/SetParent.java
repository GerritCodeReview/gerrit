// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ProjectAccess;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetParent extends Handler<String> {
  interface Factory {
    SetParent create(String parentName, List<Project.NameKey> childProjects);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;

  private final String parentName;
  private final List<Project.NameKey> childProjects;

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Inject
  SetParent(final ReviewDb db, final ProjectCache projectCache,
      final ProjectControl.Factory projectControlFactory,
      @Assisted final String parentName, @Assisted List<Project.NameKey> childProjects) {
    this.db = db;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;

    this.parentName = parentName;
    this.childProjects = childProjects;
  }

  @Override
  public String call() throws NoSuchProjectException, OrmException {
    StringBuilder errorMessage = null;

    final ProjectAccess projectAccess = db.projects();
    final Set<Project.NameKey> grandParents = new HashSet<Project.NameKey>();
    Project.NameKey parentNameKey = new Project.NameKey(parentName);

    grandParents.add(wildProject);

    if (parentNameKey.get().equals(wildProject.get()) || "".equals(parentNameKey.get())) {
      // If selected parent is the wild project, set to NULL.
      //
      parentNameKey = null;
    } else {
      final ProjectControl projectControl = projectControlFactory.controlFor(parentNameKey);
      if (!projectControl.isOwner()) {
        throw new NoSuchProjectException(parentNameKey);
      }

      final ProjectState state = projectCache.get(parentNameKey);
      Project.NameKey grandParent = null;

      if (state != null) {
        grandParent = state.getProject().getParent();
      }

      // Catalog all grandparents of the "parent", we want to
      // catch a cycle in the parent pointers before it occurs.
      //
      while (grandParent != null && !grandParent.equals(wildProject)) {
        grandParents.add(grandParent);
        final ProjectState s = projectCache.get(grandParent);
        if (s != null) {
          grandParent = s.getProject().getParent();
        }
        else {
          break;
        }
      }
    }

    final List<Project.NameKey> failedProjects = new ArrayList<Project.NameKey>();

    for (Project.NameKey child : childProjects) {
      Project projectChild = projectAccess.get(child);

      // If the child project doesn't exist, just skip it
      if (projectChild != null && !child.equals(wildProject.get())
          && !child.equals(parentNameKey) && (!grandParents.contains(child))) {
        projectChild.setParent(parentNameKey);
        projectAccess.update(Collections.singleton(projectChild));
      }
      else {
        failedProjects.add(child);
      }
    }

    if (failedProjects.size() > 0) {
      errorMessage = new StringBuilder();

      errorMessage.append("It was not possible to set the parent project "
          + parentName + " to the following projects: ");

      for (Project.NameKey p : failedProjects) {
        errorMessage.append(p.get() + " ");
      }
    }

    // invalidates all projects on cache
    projectCache.evictAll();

    // Flag to indicate that all "set parent" operations failed
    if (failedProjects.size() == childProjects.size()) {
      errorMessage.insert(0, "All:");
    }

    String message = "";
    if (errorMessage != null) {
      message = errorMessage.toString();
    }

    return message;
  }
}
