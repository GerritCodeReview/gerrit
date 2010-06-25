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
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

class VisibleProjects extends Handler<List<ProjectData>> {
  interface Factory {
    VisibleProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ReviewDb db;

  private static final Project.NameKey NOT_VISIBLE_PROJECT =
      new Project.NameKey("(x)");

  @Inject
  @WildProjectName
  private Project.NameKey wildProject;

  @Inject
  VisibleProjects(final ProjectControl.Factory projectControlFactory,
      final ReviewDb db) {
    this.projectControlFactory = projectControlFactory;
    this.db = db;
  }

  @Override
  public List<ProjectData> call() throws OrmException {
    final List<ProjectData> result;
    final TreeMap<String, Project> projectsMap = new TreeMap<String, Project>();

    for (final Project p : db.projects().all().toList()) {
      projectsMap.put(p.getName(), p);
    }

    result = new ArrayList<ProjectData>();
    int parentId = 0;

    for (Project p : projectsMap.values()) {
      try {
        boolean isWildProject = false;

        NameKey parentNameKey = null;
        if (p.getParent() != null) {
          parentNameKey = p.getParent();
          if (parentNameKey != null) {
            final Project parent = projectsMap.get(parentNameKey.get());
            if (parent != null) {
              parentId = parent.getId();
            }

            final ProjectControl parentControl =
                projectControlFactory.controlFor(p.getParent());
            if (!parentControl.isVisible() && !parentControl.isOwner()) {
              parentNameKey = NOT_VISIBLE_PROJECT;
            }
          }
        } else {
          final ProjectControl c =
              projectControlFactory.controlFor(p.getNameKey());
          if (!c.getProjectState().isSpecialWildProject()) {
            final Project parent = projectsMap.get((String) wildProject.get());
            parentId = parent.getId();
          } else {
            isWildProject = true;
          }
        }

        final ProjectControl c =
            projectControlFactory.controlFor(p.getNameKey());

        if (c.isVisible() || c.isOwner()) {
          result.add(new ProjectData(p.getNameKey(), p.getDescription(),
              parentId, parentNameKey, p.getId(), true, isWildProject));
        } else {
          result.add(new ProjectData(NOT_VISIBLE_PROJECT, null, parentId,
              parentNameKey, p.getId(), false, isWildProject));
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }

    return result;
  }
}
