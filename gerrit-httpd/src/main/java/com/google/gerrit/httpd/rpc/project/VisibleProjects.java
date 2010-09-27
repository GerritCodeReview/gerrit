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
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class VisibleProjects extends Handler<List<ProjectData>> {
  interface Factory {
    VisibleProjects create();
  }

  private final ProjectControl.Factory projectControlFactory;
  private final CurrentUser user;
  private final ReviewDb db;

  @Inject
  VisibleProjects(final ProjectControl.Factory projectControlFactory,
      final CurrentUser user, final ReviewDb db) {
    this.projectControlFactory = projectControlFactory;
    this.user = user;
    this.db = db;
  }

  @Override
  public List<ProjectData> call() throws OrmException {
    final List<ProjectData> result = new ArrayList<ProjectData>();

    for(final Project p : db.projects().all().toList()) {
      boolean canBeDeleted = false;

      final List<Change> changes = db.changes().byProjectHaschange(p.getNameKey()).toList();

      try {
        final ProjectControl c = projectControlFactory.controlFor(p.getNameKey());
        //Administrators users are also considered in method "isOwner".
        if (c.isVisible() || c.isOwner()) {
          if (c.isOwner() && changes.size() == 0) {
            canBeDeleted = true;
          }

          final ProjectData projectData = new ProjectData(p.getNameKey(), p.getDescription(), canBeDeleted);
          result.add(projectData);
        }
      } catch (NoSuchProjectException e) {
        continue;
      }
    }
    Collections.sort(result, new Comparator<ProjectData>() {
      public int compare(final ProjectData a, final ProjectData b) {
        return a.getName().compareTo(b.getName());
      }
    });
    return result;
  }
}
