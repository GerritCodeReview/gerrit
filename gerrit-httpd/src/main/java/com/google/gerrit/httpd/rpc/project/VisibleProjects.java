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

import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.common.data.ProjectRightsBased;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

class VisibleProjects extends Handler<List<ProjectRightsBased>> {
  interface Factory {
    VisibleProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final CurrentUser user;
  private final ReviewDb db;

  private static final Project.NameKey NOT_VISIBLE_PROJECT = new Project.NameKey("(x)");

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Inject
  VisibleProjects(final ProjectControl.Factory projectControlFactory,
      final CurrentUser user, final ReviewDb db) {
    this.projectControlFactory = projectControlFactory;
    this.user = user;
    this.db = db;
  }

  @Override
  public List<ProjectRightsBased> call() throws OrmException {
    final List<ProjectRightsBased> result;
    final TreeMap<String, Project> projectsMap = new TreeMap<String, Project>();

    for(final Project p : db.projects().all().toList()) {
      projectsMap.put(p.getName(), p);
    }

    result = new ArrayList<ProjectRightsBased>();
    ProjectData projectData;
    int parentId = 0;

    for (Project p : projectsMap.values()) {
      try {
        String parentName = null;
        if (p.getParent() != null) {
          parentName = p.getParent().get();
          if (parentName != null) {
            final Project parent = projectsMap.get((String)parentName);
            if (parent != null) {
              parentId = parent.getId();
            }

            final ProjectControl parentControl = projectControlFactory.controlFor(p.getParent());
            if (!parentControl.isVisible() || !parentControl.isOwner()) {
              parentName = NOT_VISIBLE_PROJECT.get();
            }
          }
        } else {
          final ProjectControl c = projectControlFactory.controlFor(p.getNameKey());
          if (!c.getProjectState().isSpecialWildProject()) {
            final Project parent = projectsMap.get((String)wildProject.get());
            parentId = parent.getId();
          }
        }

        if (user.isAdministrator()) {
          projectData = new ProjectData(p.getNameKey(), p.getDescription(),
              parentId, parentName, p.getId());
          result.add(new ProjectRightsBased(projectData, true));
        } else {
          final ProjectControl c = projectControlFactory.controlFor(p.getNameKey());

          if (c.isVisible() || c.isOwner()) {
            projectData = new ProjectData(p.getNameKey(), p.getDescription(),
                parentId, parentName, p.getId());
            result.add(new ProjectRightsBased(projectData, true));
          } else {
            projectData = new ProjectData(NOT_VISIBLE_PROJECT, null,
                parentId, parentName, p.getId());
            result.add(new ProjectRightsBased(projectData, false));
          }
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }

    return result;
  }
}