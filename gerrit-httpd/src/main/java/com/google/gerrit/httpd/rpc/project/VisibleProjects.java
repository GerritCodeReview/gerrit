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


import com.google.gerrit.common.CollectionsUtil;
import com.google.gerrit.common.data.ProjectList;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ProjectCreatorGroups;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

class VisibleProjects extends Handler<ProjectList> {
  interface Factory {
    VisibleProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final CurrentUser user;
  private final ReviewDb db;
  private final Set<AccountGroup.Id> projectCreatorGroups;

  @Inject
  VisibleProjects(final ProjectControl.Factory projectControlFactory,
      final CurrentUser user, final ReviewDb db,
      @ProjectCreatorGroups Set<AccountGroup.Id> projectCreatorGroups) {
    this.projectControlFactory = projectControlFactory;
    this.user = user;
    this.db = db;
    this.projectCreatorGroups = projectCreatorGroups;
  }

  @Override
  public ProjectList call() throws OrmException {
    final ProjectList result = new ProjectList();

    List<Project> projects = getProjects();
    result.setProjects(projects);

    result.setCanCreateProject(CollectionsUtil.isAnyIncludedIn(user
        .getEffectiveGroups(), projectCreatorGroups));

    return result;
  }

  private List<Project> getProjects() throws OrmException {
    final List<Project> projects;
    if (user.isAdministrator()) {
      projects = db.projects().all().toList();
    } else {
      projects = new ArrayList<Project>();
      for (Project p : db.projects().all().toList()) {
        try {
          ProjectControl c = projectControlFactory.controlFor(p.getNameKey());
          if (c.isVisible() || c.isOwner()) {
            projects.add(p);
          }
        } catch (NoSuchProjectException e) {
          continue;
        }
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
