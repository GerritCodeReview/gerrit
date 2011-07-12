// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.data.ProjectList;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class VisibleProjects extends Handler<ProjectList> {
  interface Factory {
    VisibleProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final CurrentUser user;

  @Inject
  VisibleProjects(final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final CurrentUser user) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.user = user;
  }

  @Override
  public ProjectList call() {
    final ProjectList result = new ProjectList();

    List<Project> projects = getProjects();
    result.setProjects(projects);

    result.setCanCreateProject(user.getCapabilities().canCreateProject());

    return result;
  }

  private List<Project> getProjects() {
    final List<Project> projects = new ArrayList<Project>();
    for (Project.NameKey p : projectCache.all()) {
      try {
        ProjectControl c = projectControlFactory.controlFor(p);
        if (c.isVisible() || c.isOwner()) {
          projects.add(c.getProject());
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }
    Collections.sort(projects, new Comparator<Project>() {
      public int compare(final Project a, final Project b) {
        return a.getName().compareTo(b.getName());
      }
    });
    return projects;
  }
}
